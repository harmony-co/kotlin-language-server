package org.javacs.kt.util

/**
 * Computes a string distance using a slightly modified
 * variant of the SIFT4 algorithm in linear time.
 * Note that the function is asymmetric with respect to
 * its two input strings and thus is not a metric in the
 * mathematical sense.
 *
 * Based on the JavaScript implementation from
 * https://siderite.dev/blog/super-fast-and-accurate-string-distance.html/
 *
 * @param candidate The first string
 * @param pattern The second string
 * @param maxOffset The number of characters to search for matching letters
 */
fun stringDistance(candidate: CharSequence, pattern: CharSequence, maxOffset: Int = 4): Int = when {
    candidate.isEmpty() -> pattern.length
    pattern.isEmpty() -> candidate.length
    else -> {
        val candidateLength = candidate.length
        val patternLength = pattern.length
        var iCandidate = 0
        var iPattern = 0
        var longestCommonSubsequence = 0
        var localCommonSubstring = 0

        while (iCandidate < candidateLength && iPattern < patternLength) {
            if (candidate[iCandidate] == pattern[iPattern]) {
                localCommonSubstring++
            } else {
                longestCommonSubsequence += localCommonSubstring
                localCommonSubstring = 0

                // Move the indices to a common point, simplifying the synchronization logic
                if (iCandidate != iPattern) {
                    val iMax = Math.max(iCandidate, iPattern)
                    iCandidate = iMax
                    iPattern = iMax
                    if (iMax >= Math.min(candidateLength, patternLength)) {
                        break
                    }
                }

                // Extracted the search logic into a helper function
                val matchOffset = findMatchOffset(candidate, pattern, iCandidate, iPattern, maxOffset)
                if (matchOffset != null) {
                    iCandidate = matchOffset.first
                    iPattern = matchOffset.second
                    localCommonSubstring++
                }
            }

            iCandidate++
            iPattern++
        }

        longestCommonSubsequence += localCommonSubstring
        Math.max(candidateLength, patternLength) - longestCommonSubsequence
    }
}

/**
 * Helper function to find the offset for matching characters within the search window.
 * Returns a pair of new indices (iCandidate, iPattern) if a match is found, or null if no match is found.
 */
private fun findMatchOffset(
    candidate: CharSequence,
    pattern: CharSequence,
    iCandidate: Int,
    iPattern: Int,
    maxOffset: Int
): Pair<Int, Int>? {
    var result: Pair<Int, Int>? = null
    var foundMatch = false

    for (i in 0 until maxOffset) {
        if (!foundMatch && (iCandidate + i) < candidate.length && candidate[iCandidate + i] == pattern[iPattern]) {
            result = Pair(iCandidate + i, iPattern)
            foundMatch = true
        } else if (!foundMatch && (iPattern + i) < pattern.length && candidate[iCandidate] == pattern[iPattern + i]) {
            result = Pair(iCandidate, iPattern + i)
            foundMatch = true
        }

        // Exit loop if a match was found, no need for further iterations
        if (foundMatch) {
            break
        }
    }

    return result
}

/** Checks whether the candidate contains the pattern in order. */
fun containsCharactersInOrder(candidate: CharSequence, pattern: CharSequence, caseSensitive: Boolean): Boolean {
    var iCandidate = 0
    var iPattern = 0

    while (iCandidate < candidate.length && iPattern < pattern.length) {
        var patternChar = pattern[iPattern]
        var testChar = candidate[iCandidate]

        if (!caseSensitive) {
            patternChar = patternChar.lowercaseChar()
            testChar = testChar.lowercaseChar()
        }

        if (patternChar == testChar) {
            iPattern++
        }
        iCandidate++
    }

    return iPattern == pattern.length
}
