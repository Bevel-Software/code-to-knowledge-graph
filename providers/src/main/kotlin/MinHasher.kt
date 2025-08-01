package software.bevel.code_to_knowledge_graph.providers

import com.dynatrace.hash4j.hashing.Hashing
import com.dynatrace.hash4j.similarity.ElementHashProvider
import com.dynatrace.hash4j.similarity.SimilarityHashing
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import software.bevel.graph_domain.hashing.LocalitySensitiveHasher
import software.bevel.graph_domain.tokenizers.SemanticTokenizer
import java.util.*
import java.util.function.ToLongFunction
import kotlin.math.pow

/**
 * Implements [LocalitySensitiveHasher] using the MinHash algorithm provided by the `hash4j` library.
 * This class is designed to generate compact hash signatures (MinHash signatures) for input strings (typically source code)
 * such that the similarity between two signatures (e.g., Jaccard index) can be used to estimate the similarity
 * of the original strings. This is useful for detecting near-duplicate code snippets.
 *
 * @property tokenizer The [SemanticTokenizer] used to break down the input string into a set of elements (tokens)
 *                     before hashing. Defaults to [CharSemanticTokenizer].
 * @property logger The [Logger] instance for logging messages. Defaults to a logger for the [MinHasher] class.
 */
class MinHasher(
    val tokenizer: SemanticTokenizer = CharSemanticTokenizer(),
    val logger: Logger = LoggerFactory.getLogger(MinHasher::class.java)
): LocalitySensitiveHasher {
    /**
     * The number of components (hash values) in the MinHash signature.
     * A higher number generally leads to better accuracy but a larger signature.
     */
    val numberOfComponents = 32 //24 characters per hash
    /**
     * The number of bits used for each component in the MinHash signature.
     */
    val bitsPerComponent = 1
    /**
     * The SuperMinHash policy configured with [numberOfComponents] and [bitsPerComponent].
     * This policy defines how the MinHash signatures are generated and compared.
     */
    val policy = SimilarityHashing.superMinHash(numberOfComponents, bitsPerComponent)
    /**
     * The hasher instance created from the [policy], used to compute MinHash signatures.
     */
    val simHasher = policy.createHasher()

    /**
     * Generates a MinHash signature for the given input string.
     * The input string is first tokenized using the configured [tokenizer].
     * The resulting signature is a Base64 encoded string.
     *
     * @param input The string to hash.
     * @return A Base64 encoded string representing the MinHash signature of the input.
     *         Returns an empty string if an error occurs during hashing (though current implementation might throw).
     */
    override fun hash(input: String): String {
        val stringHashFunc = ToLongFunction { s: String? ->
            Hashing.komihash5_0().hashCharsToLong(s)
        }

        val tokens = tokenizer.tokenizeCode(input).ifEmpty { setOf(input) }

        val signature: ByteArray = simHasher.compute(ElementHashProvider.ofCollection(tokens, stringHashFunc))

        return Base64.getEncoder().encodeToString(signature)
    }

    /**
     * Estimates the Jaccard similarity between two MinHash signatures.
     * The input hashes are expected to be Base64 encoded strings generated by the [hash] method.
     *
     * @param hash1 The first Base64 encoded MinHash signature.
     * @param hash2 The second Base64 encoded MinHash signature.
     * @return A Double value between 0.0 and 1.0 representing the estimated Jaccard similarity.
     *         Returns 1.0 if hashes are identical, 0.0 if either hash is empty or an error occurs during decoding/comparison.
     */
    override fun similarity(hash1: String, hash2: String): Double {
        if(hash1 == hash2) return 1.0
        if(hash1 == "" || hash2 == "") return 0.0

        try {
            val bytes1 = Base64.getDecoder().decode(hash1)
            val bytes2 = Base64.getDecoder().decode(hash2)

            val fractionOfEqualComponents = policy.getFractionOfEqualComponents(bytes1, bytes2)

            // Formula to estimate Jaccard similarity from fraction of equal components for SuperMinHash
            // J_est = (f - 2^-b) / (1 - 2^-b)
            // where f is fractionOfEqualComponents and b is bitsPerComponent
            val estimatedJaccardSimilarity = ((fractionOfEqualComponents - 2.0.pow(-bitsPerComponent.toDouble()))
                    / (1.0 - 2.0.pow(-bitsPerComponent.toDouble())))

            return estimatedJaccardSimilarity
        } catch (ex: Exception) {
            logger.error("Error computing similarity for hashes: $hash1 and $hash2", ex)
            return 0.0
        }
    }
}