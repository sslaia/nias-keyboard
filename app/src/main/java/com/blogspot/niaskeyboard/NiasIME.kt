package com.blogspot.niaskeyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PorterDuff
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class NiasIME : InputMethodService() {

    private lateinit var keyboardContainer: FrameLayout
    private lateinit var inputContainer: View
    private lateinit var candidateContainer: LinearLayout
    private lateinit var candidateScroll: View
    
    private var niasWords = mutableSetOf<String>()
    private var indoWords = mutableSetOf<String>()
    private var learnedWords = mutableSetOf<String>()
    
    private var isNiasMode = true
    private var isCaps = false
    private var isShiftLocked = false
    private var lastShiftTime: Long = 0
    private var lastSpaceTime: Long = 0
    private var isNumericMode = false

    override fun onCreate() {
        super.onCreate()
        loadDictionary("nias-dict.txt", niasWords)
        loadDictionary("indo-dict.txt", indoWords)
        loadLearnedWords()
    }

    private fun loadDictionary(fileName: String, targetSet: MutableSet<String>) {
        try {
            assets.open(fileName).bufferedReader().useLines { lines ->
                lines.forEach { targetSet.add(it.trim().lowercase()) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadLearnedWords() {
        val prefs = getSharedPreferences("nias_learned_dict", Context.MODE_PRIVATE)
        learnedWords.addAll(prefs.getStringSet("words", emptySet()) ?: emptySet())
    }

    private fun learnWord(word: String) {
        val cleanWord = word.trim().lowercase()
        if (cleanWord.length > 1 && !niasWords.contains(cleanWord) && !indoWords.contains(cleanWord)) {
            if (learnedWords.add(cleanWord)) {
                val prefs = getSharedPreferences("nias_learned_dict", Context.MODE_PRIVATE)
                prefs.edit().putStringSet("words", learnedWords).apply()
            }
        }
    }

    private fun isDarkMode(): Boolean {
        val prefs = getSharedPreferences("nias_prefs", Context.MODE_PRIVATE)
        val forceDark = prefs.getBoolean("dark_mode", false)
        if (forceDark) return true
        
        val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
    }

    override fun onCreateInputView(): View {
        inputContainer = layoutInflater.inflate(R.layout.keyboard_view, null)
        keyboardContainer = inputContainer.findViewById(R.id.keyboard_container)
        candidateContainer = inputContainer.findViewById(R.id.candidate_container)
        candidateScroll = inputContainer.findViewById(R.id.candidate_scroll)
        
        ViewCompat.setOnApplyWindowInsetsListener(inputContainer) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val spacer = v.findViewById<View>(R.id.keyboard_bottom_spacer)
            val params = spacer?.layoutParams
            if (params != null) {
                params.height = Math.max(systemBars.bottom, navBars.bottom)
                spacer.layoutParams = params
            }
            insets
        }

        updateKeyboardLayout()
        
        return inputContainer
    }

    private fun updateKeyboardLayout() {
        keyboardContainer.removeAllViews()
        val layoutId = when {
            isNumericMode -> R.layout.keyboard_numeric
            isNiasMode -> R.layout.keyboard_nias
            else -> R.layout.keyboard_indo
        }
        
        val safeLayoutId = if (layoutId == 0) R.layout.keyboard_nias else layoutId
        
        val keyboardView = layoutInflater.inflate(safeLayoutId, keyboardContainer, false)
        bindKeyClicks(keyboardView)
        updateKeyLabels(keyboardView)
        applyThemeToKeys(keyboardView)
        keyboardContainer.addView(keyboardView)
    }

    private fun bindKeyClicks(parent: View) {
        if (parent is ViewGroup) {
            for (i in 0 until parent.childCount) {
                bindKeyClicks(parent.getChildAt(i))
            }
        } else if (parent is Button) {
            parent.setOnClickListener {
                val code = (it.tag as? String)?.toInt() ?: 0
                handleKey(code, it)
            }
            
            val code = (parent.tag as? String)?.toInt() ?: 0
            if (isSpecialKey(code)) {
                parent.setOnLongClickListener {
                    showLongPressOptions(it, code)
                    true
                }
            }
        }
    }

    private fun isSpecialKey(code: Int): Boolean {
        // Includes , . a e i o u n s l w emoji
        return code == 44 || code == 46 || 
               code == 97 || code == 101 || code == 105 || code == 111 || code == 117 ||
               code == 110 || code == 115 || code == 108 || code == 119 || code == -101
    }

    private fun showLongPressOptions(view: View, code: Int) {
        if (code == -101) {
            showEmojiPopup(view)
            return
        }

        val options = when (code) {
            44 -> "!?-"
            46 -> "±~|{}[]"
            97 -> "åãâáàä"
            101 -> "ęēëêéè€"
            105 -> "ïîíì"
            111 -> "õǒőōôóòö"
            117 -> "ûúùü"
            110 -> "ñ"
            115 -> "ß\$"
            108 -> "£"
            119 -> "ŵ"
            else -> return
        }
        
        val popupMenu = PopupMenu(this, view)
        options.forEachIndexed { index, char ->
            popupMenu.menu.add(0, index, index, char.toString())
        }
        popupMenu.setOnMenuItemClickListener { item ->
            val char = options[item.itemId]
            handleLongPressSelection(char)
            true
        }
        popupMenu.show()
    }

    private fun handleLongPressSelection(char: Char) {
        val ic = currentInputConnection ?: return
        var finalChar = char
        if (isCaps && char.isLetter()) {
            finalChar = char.uppercaseChar()
        }
        ic.commitText(finalChar.toString(), 1)
        
        if (isCaps && !isShiftLocked) {
            isCaps = false
            updateKeyLabels(keyboardContainer)
        }
    }

    private fun updateKeyLabels(parent: View) {
        if (parent is ViewGroup) {
            for (i in 0 until parent.childCount) {
                updateKeyLabels(parent.getChildAt(i))
            }
        } else if (parent is Button) {
            val code = (parent.tag as? String)?.toInt() ?: 0
            if (code == 32) {
                parent.text = when {
                    isNumericMode -> ""
                    isNiasMode -> "LI NIHA"
                    else -> "INDONESIA"
                }
            } else if (code == -1) {
                parent.text = if (isShiftLocked) "🔒⬆️" else "⬆️"
            } else if (code > 0) {
                val char = code.toChar()
                parent.text = if (isCaps) char.uppercaseChar().toString() else char.lowercaseChar().toString()
            }
        }
    }

    private fun applyThemeToKeys(parent: View) {
        val isDark = isDarkMode()
        
        val keyColor = ContextCompat.getColor(this, if (isDark) R.color.gboard_dark_key else R.color.gboard_light_key)
        val textColor = if (isDark) Color.WHITE else Color.BLACK
        val hintColor = if (isDark) Color.argb(60, 255, 255, 255) else Color.argb(60, 0, 0, 0)

        if (parent is ViewGroup) {
            for (i in 0 until parent.childCount) {
                applyThemeToKeys(parent.getChildAt(i))
            }
        } else if (parent is Button) {
            parent.background.mutate().setColorFilter(keyColor, PorterDuff.Mode.SRC_IN)
            val code = (parent.tag as? String)?.toInt() ?: 0
            if (code == 32) {
                parent.setTextColor(hintColor)
                parent.textSize = 14f
            } else if (code < 0) {
                parent.setTextColor(textColor)
                parent.textSize = 18f
            } else {
                parent.setTextColor(textColor)
                parent.textSize = 22f
            }
        }
    }

    private fun handleKey(primaryCode: Int, view: View? = null) {
        val ic = currentInputConnection ?: return

        when (primaryCode) {
            -101 -> { // Emoji
                view?.let { showEmojiPopup(it) }
            }
            -5 -> { // Delete
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
                updateSuggestionsAfterDelete()
                checkAutoCaps()
            }
            -1 -> { // Shift
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastShiftTime < 400) {
                    isShiftLocked = !isShiftLocked
                    isCaps = isShiftLocked
                } else {
                    if (isShiftLocked) {
                        isShiftLocked = false
                        isCaps = false
                    } else {
                        isCaps = !isCaps
                    }
                }
                lastShiftTime = currentTime
                updateKeyLabels(keyboardContainer)
            }
            -4 -> { // Enter/Done
                handleWordCompletion()
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                checkAutoCaps()
            }
            -2 -> { // Mode Change
                isNumericMode = !isNumericMode
                updateKeyboardLayout()
            }
            32 -> { // Space bar
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastSpaceTime < 400) {
                    val textBefore = ic.getTextBeforeCursor(1, 0)?.toString() ?: ""
                    if (textBefore == " ") {
                        ic.deleteSurroundingText(1, 0)
                        ic.commitText(". ", 1)
                        lastSpaceTime = 0
                    } else {
                        handleWordCompletion()
                        ic.commitText(" ", 1)
                        lastSpaceTime = currentTime
                    }
                } else {
                    handleWordCompletion()
                    ic.commitText(" ", 1)
                    lastSpaceTime = currentTime
                }
                checkAutoCaps()
            }
            -100 -> { // Language Switch
                isNiasMode = !isNiasMode
                isNumericMode = false
                updateKeyboardLayout()
                Toast.makeText(this, if(isNiasMode) "Nias" else "Indonesia", Toast.LENGTH_SHORT).show()
                candidateContainer.removeAllViews()
                candidateScroll.visibility = View.GONE
            }
            else -> {
                var char = primaryCode.toChar()
                if (isCaps && char.isLetter()) {
                    char = char.uppercaseChar()
                }
                ic.commitText(char.toString(), 1)

                if (isCaps && !isShiftLocked) {
                    isCaps = false
                    updateKeyLabels(keyboardContainer)
                }

                val content = ic.getTextBeforeCursor(20, 0)
                suggestWords(content?.toString() ?: "")
                lastSpaceTime = 0
            }
        }
    }

    private fun checkAutoCaps() {
        if (isShiftLocked) return
        
        val ic = currentInputConnection ?: return
        val textBefore = ic.getTextBeforeCursor(5, 0)?.toString() ?: ""
        
        val shouldCap = if (textBefore.isEmpty()) {
            true
        } else {
            val trimmed = textBefore.trimEnd()
            if (trimmed.isEmpty()) {
                true
            } else {
                val lastChar = trimmed.last()
                lastChar == '.' || lastChar == '!' || lastChar == '?' || lastChar == '\n'
            }
        }
        
        if (isCaps != shouldCap) {
            isCaps = shouldCap
            updateKeyLabels(keyboardContainer)
        }
    }

    private fun showEmojiPopup(anchor: View) {
        val popupMenu = PopupMenu(this, anchor)
        val emojis = listOf("😀", "😂", "😍", "👍", "🙏", "✨", "🎉", "❤️", "😊", "😎")
        emojis.forEachIndexed { index, emoji ->
            popupMenu.menu.add(0, index, index, emoji)
        }
        popupMenu.setOnMenuItemClickListener { item ->
            val emoji = emojis[item.itemId]
            currentInputConnection?.commitText(emoji, 1)
            true
        }
        popupMenu.show()
    }

    private fun suggestWords(input: String) {
        if (!::candidateContainer.isInitialized || !::candidateScroll.isInitialized) return
        
        candidateContainer.removeAllViews()
        
        if (input.isEmpty()) {
            candidateScroll.visibility = View.GONE
            return
        }

        val lastWord = input.substringAfterLast(' ', input).lowercase()
        if (lastWord.length < 2) {
            candidateScroll.visibility = View.GONE
            return
        }

        val currentDict = if (isNiasMode) niasWords else indoWords
        val matches = (currentDict + learnedWords)
            .filter { it.startsWith(lastWord) }
            .take(5)

        if (matches.isEmpty()) {
            candidateScroll.visibility = View.GONE
            return
        }

        val isDark = isDarkMode()
        val textColor = if (isDark) Color.WHITE else Color.BLACK

        for (word in matches) {
            val tv = TextView(this)
            tv.text = word
            tv.setTextColor(textColor)
            tv.setPadding(35, 20, 35, 20)
            tv.textSize = 20f
            tv.setOnClickListener {
                val ic = currentInputConnection
                val originalWord = input.substringAfterLast(' ', input)
                val isFirstUpper = originalWord.isNotEmpty() && originalWord[0].isUpperCase()
                
                val finalWord = if (isFirstUpper) {
                    word.replaceFirstChar { it.uppercase() }
                } else {
                    word
                }

                ic?.deleteSurroundingText(originalWord.length, 0)
                ic?.commitText("$finalWord ", 1)
                candidateContainer.removeAllViews()
                candidateScroll.visibility = View.GONE
                
                checkAutoCaps()
            }
            candidateContainer.addView(tv)
        }
        candidateScroll.visibility = View.VISIBLE
    }

    private fun handleWordCompletion() {
        val ic = currentInputConnection
        val content = ic?.getTextBeforeCursor(30, 0)?.toString() ?: ""
        val lastWord = content.substringAfterLast(' ', "").trim()
        if (lastWord.isNotEmpty()) {
            learnWord(lastWord)
        }
        candidateContainer.removeAllViews()
        candidateScroll.visibility = View.GONE
    }

    private fun updateSuggestionsAfterDelete() {
        val ic = currentInputConnection
        val content = ic?.getTextBeforeCursor(20, 0)
        suggestWords(content?.toString() ?: "")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::inputContainer.isInitialized) {
            applyTheme()
        }
    }

    private fun applyTheme() {
        val isDark = isDarkMode()
        
        val bgColor = if (isDark) {
            ContextCompat.getColor(this, R.color.gboard_dark_bg)
        } else {
            ContextCompat.getColor(this, R.color.gboard_light_bg)
        }
        
        inputContainer.setBackgroundColor(bgColor)
        
        val bottomSpacer = inputContainer.findViewById<View>(R.id.keyboard_bottom_spacer)
        bottomSpacer?.setBackgroundColor(bgColor)
        
        if (::candidateScroll.isInitialized) {
            candidateScroll.setBackgroundColor(bgColor)
        }

        window?.window?.let { win ->
            win.navigationBarColor = bgColor
            val controller = WindowInsetsControllerCompat(win, win.decorView)
            controller.isAppearanceLightNavigationBars = !isDark
        }
        
        applyThemeToKeys(keyboardContainer)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        applyTheme()
        if (::candidateScroll.isInitialized) {
            candidateScroll.visibility = View.GONE
        }
        checkAutoCaps()
    }
}
