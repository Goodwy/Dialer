package com.goodwy.dialer.helpers

import android.telephony.PhoneNumberUtils
import com.google.gson.JsonParser

private typealias CharMap = HashMap<Char, Int>

/**
 * A helper class to map chars of various alphabets to numbers
 */
object DialpadT9 {

    private class T9Language {
        val letters = Array(8) {""}
        val charMap = CharMap()
    }

    private val languages = HashMap<String, T9Language>()

    /**
     * Create a map for Latin (English) characters according to ITU E.161 and ISO/IEC 9995-8
     */
    private fun initLatinChars() {
        val lang = T9Language()
        val abc = 'A'..'Z'
        val num = '0'..'9'
        val abcNum = abc + num
        for (c in abcNum) {
            val number = PhoneNumberUtils.convertKeypadLettersToDigits(c.toString()).toInt()
            if(number in 2..9) {
                lang.letters[number - 2] += c.toString()
                lang.charMap[c] = number
            }
        }

        languages[LOCALE_EN] = lang
    }

    var Initialized = false

    init {
        initLatinChars()
    }

    /**
     * Read extra languages data from JSON
     */
    fun readFromJson(jsonText: String) {
        for (arrayElement in JsonParser.parseString(jsonText).asJsonArray) {
            val objectElement = arrayElement.asJsonObject
            val lang = objectElement["lang"].asString
            if (languages[lang] != null) {
                continue
            }
            val language = T9Language()
            for ((i, lettersElement) in objectElement["letters"].asJsonArray.withIndex()) {
                val letters = lettersElement.asString
                language.letters[i] = letters
                for (c in letters) {
                    language.charMap[c] = i + 2
                }
            }
            languages[lang] = language
        }

        Initialized = true
    }

    fun getLettersForNumber(number: Int, language: String): String {
        val lang = languages[language]
        return if (lang == null || number < 2 || number > 9) {
            ""
        } else {
            lang.letters[number - 2]
        }
    }

    fun convertLettersToNumbers(letters: String, language: String): String {
        var res = ""
        val lang = languages[language]
        val langEn = languages[LOCALE_EN]

        for (c in letters) {
            if (lang != null && lang.charMap.containsKey(c)) {
                res += lang.charMap[c]
            } else if (langEn != null && langEn.charMap.containsKey(c)) {
                res += langEn.charMap[c]
            }
        }

        return res
    }

    fun getSupportedSecondaryLanguages(): List<String> {
        val res = MutableList(0) {""}
        for (lang in languages.keys) {
            if (lang != LOCALE_EN)
                res += lang
        }
        res.sort()
        return res
    }
}
