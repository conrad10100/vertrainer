package com.loadedvj.backend.rag;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A free (no-API-call) evaluation harness for ChunkingService.chunkTranscript(). Instead of just
 * checking a handful of hand-picked example sentences, this runs the real boundary logic against
 * several realistic, multi-topic transcript fixtures and scores it on properties that matter for
 * retrieval quality:
 *
 * 1. Coverage -- every sentence in the source transcript survives in the output, in order, with
 *    only the intended one-sentence overlap duplicated at each boundary. A chunker that drops or
 *    reorders content would fail retrieval silently, so this is a hard assertion.
 * 2. Size discipline -- no chunk exceeds the configured max.
 * 3. Separation quality -- the actual signal a "sliding window with similarity" chunker is meant
 *    to provide: sentences *within* a chunk should be more lexically similar to each other, on
 *    average, than chunks are to their neighbors across a boundary. This is scored, not just
 *    eyeballed, using the same word-overlap cosine similarity the chunker itself uses (so this
 *    stays free to run -- no embeddings, no Claude calls).
 *
 * Run with: mvn -Dtest=ChunkingQualityEvalTest test
 */
class ChunkingQualityEvalTest {

    private final ChunkingService service = new ChunkingService();

    @Test
    void evaluatesTranscriptChunkingAcrossMultiTopicFixtures() {
        List<EvalResult> results = new ArrayList<>();
        List<Double> allIntraSimilarities = new ArrayList<>();
        List<Double> allInterBoundarySimilarities = new ArrayList<>();

        for (Fixture fixture : FIXTURES) {
            EvalResult result = evaluate(fixture);
            results.add(result);
            allIntraSimilarities.addAll(result.intraSimilarities);
            allInterBoundarySimilarities.addAll(result.interBoundarySimilarities);

            // Hard invariants: no content lost or reordered beyond the intended overlap.
            assertThat(result.reconstructedSentences)
                .as("chunking '%s' must reproduce every original sentence in order", fixture.name)
                .isEqualTo(fixture.originalSentences);

            // Hard invariant: size discipline.
            assertThat(result.maxChunkWords)
                .as("no chunk in '%s' should exceed the configured max", fixture.name)
                .isLessThanOrEqualTo(ChunkingService.TRANSCRIPT_MAX_WORDS);
        }

        // Quality signal, pooled across every fixture rather than checked per-fixture: any single
        // multi-topic fixture only has a handful of boundaries, so a per-fixture comparison is
        // noisy (one unusually word-heavy sentence pair can flip it). Pooling every intra-chunk
        // sentence pair against every boundary pair across all fixtures gives a big enough sample
        // that the comparison actually reflects whether the chunker separates topics better than
        // chance, instead of reflecting sampling noise in any one fixture.
        double pooledIntra = average(allIntraSimilarities);
        double pooledInter = average(allInterBoundarySimilarities);
        System.out.printf("%npooled: intra-sim=%.3f (n=%d)  inter-sim=%.3f (n=%d)  gap=%.3f%n",
            pooledIntra, allIntraSimilarities.size(), pooledInter, allInterBoundarySimilarities.size(),
            pooledIntra - pooledInter);
        assertThat(pooledIntra)
            .as("pooled within-chunk similarity should exceed pooled across-boundary similarity")
            .isGreaterThan(pooledInter);

        printReport(results);
    }

    private static double average(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private EvalResult evaluate(Fixture fixture) {
        List<String> chunks = service.chunkTranscript(fixture.text);

        List<String> reconstructed = reconstructSentences(chunks);

        List<Integer> chunkWordCounts = chunks.stream().map(ChunkingService::wordCount).toList();
        int maxChunkWords = chunkWordCounts.stream().mapToInt(Integer::intValue).max().orElse(0);
        double avgChunkWords = chunkWordCounts.stream().mapToInt(Integer::intValue).average().orElse(0);

        List<Double> intraSimilarities = intraChunkSimilarities(chunks);
        List<Double> interBoundarySimilarities = interBoundarySimilarities(chunks);

        return new EvalResult(fixture.name, chunks.size(), avgChunkWords, maxChunkWords,
            reconstructed, intraSimilarities, interBoundarySimilarities);
    }

    /** Re-splits each chunk into sentences and drops each boundary's duplicated overlap sentence,
     * so the result should exactly equal the original sentence sequence if nothing was lost. */
    private static List<String> reconstructSentences(List<String> chunks) {
        List<String> result = new ArrayList<>();
        for (String chunk : chunks) {
            List<String> chunkSentences = ChunkingService.splitSentences(chunk);
            for (String sentence : chunkSentences) {
                if (!result.isEmpty() && result.get(result.size() - 1).equals(sentence)) {
                    continue; // the overlap sentence carried over from the previous chunk
                }
                result.add(sentence);
            }
        }
        return result;
    }

    private static List<Double> intraChunkSimilarities(List<String> chunks) {
        List<Double> values = new ArrayList<>();
        for (String chunk : chunks) {
            List<String> sentences = ChunkingService.splitSentences(chunk);
            List<Map<String, Integer>> freqs = sentences.stream().map(ChunkingService::wordFrequencies).toList();
            for (int i = 0; i < freqs.size(); i++) {
                for (int j = i + 1; j < freqs.size(); j++) {
                    values.add(ChunkingService.cosineSimilarity(freqs.get(i), freqs.get(j)));
                }
            }
        }
        return values;
    }

    /** Sentence-level, to stay comparable with intraChunkSimilarities: the similarity between the
     * last sentence before a break and the first new sentence after it (skipping the carried-over
     * overlap sentence, which is deliberately shared and would understate the boundary). */
    private static List<Double> interBoundarySimilarities(List<String> chunks) {
        List<Double> values = new ArrayList<>();
        for (int i = 0; i < chunks.size() - 1; i++) {
            List<String> before = ChunkingService.splitSentences(chunks.get(i));
            List<String> after = ChunkingService.splitSentences(chunks.get(i + 1));
            String lastBeforeBreak = before.get(before.size() - 1);
            String firstNewAfterBreak = after.stream().filter(s -> !s.equals(lastBeforeBreak)).findFirst().orElse(after.get(after.size() - 1));
            values.add(ChunkingService.cosineSimilarity(
                ChunkingService.wordFrequencies(lastBeforeBreak), ChunkingService.wordFrequencies(firstNewAfterBreak)));
        }
        return values;
    }

    private static void printReport(List<EvalResult> results) {
        System.out.println();
        System.out.println("=== ChunkingService.chunkTranscript() quality report ===");
        System.out.printf("%-28s %7s %10s %9s %11s %11s%n",
            "fixture", "chunks", "avg words", "max words", "intra-sim", "inter-sim");
        for (EvalResult r : results) {
            String interSim = r.interBoundarySimilarities.isEmpty() ? "n/a" : String.format("%.3f", average(r.interBoundarySimilarities));
            System.out.printf("%-28s %7d %10.1f %9d %11.3f %11s%n",
                r.fixtureName, r.chunkCount, r.avgChunkWords, r.maxChunkWords, average(r.intraSimilarities), interSim);
        }
        System.out.println("(intra-sim = avg similarity between sentences in the same chunk;");
        System.out.println(" inter-sim = avg similarity between the sentences split apart at a boundary;");
        System.out.println(" the pooled comparison below is the one that's actually asserted on.)");
    }

    private record EvalResult(String fixtureName, int chunkCount, double avgChunkWords, int maxChunkWords,
                               List<String> reconstructedSentences, List<Double> intraSimilarities,
                               List<Double> interBoundarySimilarities) {
    }

    private record Fixture(String name, String text, List<String> originalSentences) {
        Fixture(String name, String text) {
            this(name, text, ChunkingService.splitSentences(text));
        }
    }

    // Hand-written, not repeated: repeating an identical block would make the boundary between two
    // copies of the same paragraph look artificially similar (literal duplicate phrasing), which
    // would mask the exact failure mode this eval exists to catch.
    private static final String SQUAT_THEN_NUTRITION = """
        Back squat depth matters for jump transfer. Hit parallel or below on every working rep. \
        Depth builds the stretch reflex you need for a real jump. Don't cut squats short just \
        because the weight feels heavy. Progressive overload on the squat drives long term \
        vertical gains over many training blocks. A weak bottom position in the squat usually \
        shows up as a weak takeoff on the court. Film your squat from the side every few weeks \
        to check your depth is consistent. Box squats can help you learn to sit back and control \
        that bottom position. Pause squats build confidence at the hardest part of the depth \
        curve. Keep your chest up through the whole squat so the bar stays over your midfoot. \
        Tight ankles often quietly limit how deep an athlete can actually squat. A good warmup \
        set at bodyweight depth primes the pattern before you add any plates. Coaches should cue \
        bar path before they ever cue speed off the bottom. Squat strength alone doesn't \
        guarantee a better vertical without matching speed work on top of it. Wide stance squats \
        can suit a longer-limbed athlete better than a narrow, upright stance. \
        Now let's shift gears and talk about protein intake for recovery between sessions. Eat \
        about one gram of protein per pound of bodyweight every single day. Recovery nutrition \
        timing around your lifting sessions matters a lot for how sore you feel the next day. \
        Spread your protein across four or five meals instead of one huge dinner. Cheap sources \
        like eggs, chicken thighs, and greek yogurt work just as well as fancy supplements. \
        Hydration affects recovery just as much as protein does, so don't skip the water. \
        Carbs around your training window refill glycogen so tomorrow's session doesn't suffer. \
        A consistent eating schedule beats a perfect one you can't stick to for more than a week. \
        Skipping meals after a hard session just slows down how fast your legs bounce back. \
        Supplements are a small multiplier on top of a solid base diet, never a replacement for one. \
        """;

    private static final String THREE_TOPIC_TRANSCRIPT = """
        Landing mechanics are where most vertical jump injuries actually happen. Land soft with \
        bent knees, never locked out straight legs. A stiff landing sends all that force straight \
        into your knees and lower back. Practice sticking your landing on every single box jump \
        rep, no exceptions. Lateral bounds teach your body to absorb force from a different angle \
        than a straight vertical landing. Depth drops train the same soft-landing pattern but with \
        more eccentric load. Your coach should watch your landing form before increasing box \
        height. Good landing mechanics carry over directly into safer cutting and change of \
        direction on the court. Athletes who land loud are usually bleeding force into the ground \
        instead of absorbing it. Regressing box height for a session is smarter than grinding \
        through a landing that keeps breaking down. A two-foot stick drill builds the control \
        single-leg landings will eventually need. \
        Switching topics, sleep is probably the most underrated part of a vertical jump program. \
        Aim for at least eight hours a night during a heavy training block. Growth hormone release \
        during deep sleep is when most of your actual physical adaptation happens. A consistent \
        bedtime matters more than most athletes realize for recovery. Cutting sleep short to fit \
        in extra training sessions usually backfires within a couple of weeks. Naps can help make \
        up a sleep deficit but they're not a full substitute for a real night's rest. Track your \
        sleep for a month and compare it against how your legs feel in training. Screens right \
        before bed can quietly wreck the deep sleep you're relying on to recover. A cool, dark room \
        makes a bigger difference for most athletes than any recovery gadget on the market. \
        One more thing before we wrap up, let's talk about mental cues during your approach. \
        Picture the rim or the target before you even start your approach steps. A confident, \
        aggressive mindset on the approach translates into a more explosive takeoff. Don't \
        overthink your steps mid-approach, trust the footwork you've already drilled. Visualization \
        the night before a session can prime your nervous system for a better jump the next day. \
        Athletes who jump tentatively almost always leave inches on the table compared to a \
        committed, aggressive approach. A short breathing cue before the approach can settle nerves \
        without slowing the whole warmup down. \
        """;

    private static final String SINGLE_TOPIC_WITH_ASIDE = ("""
        Back squat depth matters for jump transfer. Hit parallel or below on every working rep. \
        Depth builds the stretch reflex you need for a real jump. Don't cut squats short just \
        because the weight feels heavy. Progressive overload on the squat drives long term \
        vertical gains over many training blocks. """).repeat(4)
        + "By the way, my dog needs a walk after this session. "
        + "Anyway, back to squat depth and how consistent depth checks keep your numbers honest.";

    // Deliberately multi-topic, using vertrainer's own domain (vertical jump coaching) so the
    // fixtures exercise the same kind of content this chunker actually sees in production.
    private static final List<Fixture> FIXTURES = List.of(
        new Fixture("squat-to-nutrition-shift", SQUAT_THEN_NUTRITION),
        new Fixture("three-topic-transcript", THREE_TOPIC_TRANSCRIPT),
        new Fixture("single-topic-with-aside", SINGLE_TOPIC_WITH_ASIDE)
    );
}
