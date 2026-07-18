package com.example.ludoqueen

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class PlayerSelectorActivity : AppCompatActivity() {

    private var selectedPlayerCount = 4
    private var isDuoMode = false

    private lateinit var btn2Players: Button
    private lateinit var btn3Players: Button
    private lateinit var btn4Players: Button
    private lateinit var btnIndividualMode: Button
    private lateinit var btnDuoMode: Button
    private lateinit var tvSelectionSummary: TextView

    // Colors parsed cleanly for Material background tints
    private val colorSelected = ColorStateList.valueOf(Color.parseColor("#E91E63"))     // Active Accent Pink
    private val colorUnselected = ColorStateList.valueOf(Color.parseColor("#3F3F5F"))   // Dark Grayish Blue
    private val colorIndividual = ColorStateList.valueOf(Color.parseColor("#2196F3"))   // Blue Accent for Individual

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player_selector)

        btn2Players = findViewById(R.id.btn2Players)
        btn3Players = findViewById(R.id.btn3Players)
        btn4Players = findViewById(R.id.btn4Players)
        btnIndividualMode = findViewById(R.id.btnIndividualMode)
        btnDuoMode = findViewById(R.id.btnDuoMode)
        tvSelectionSummary = findViewById(R.id.tvSelectionSummary)
        val btnStartGame: Button = findViewById(R.id.btnStartGame)

        btn2Players.setOnClickListener { selectPlayerCount(2) }
        btn3Players.setOnClickListener { selectPlayerCount(3) }
        btn4Players.setOnClickListener { selectPlayerCount(4) }

        btnIndividualMode.setOnClickListener { selectMode(false) }
        btnDuoMode.setOnClickListener { selectMode(true) }

        btnStartGame.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("PLAYER_COUNT", selectedPlayerCount)
            intent.putExtra("IS_TEAM_MODE", isDuoMode)
            startActivity(intent)
        }

        refreshUiState()
    }

    private fun selectPlayerCount(count: Int) {
        selectedPlayerCount = count
        if (count != 4 && isDuoMode) {
            isDuoMode = false
        }
        refreshUiState()
    }

    private fun selectMode(duo: Boolean) {
        if (duo && selectedPlayerCount != 4) return
        isDuoMode = duo
        refreshUiState()
    }

    private fun refreshUiState() {
        // Apply responsive visual adjustments using backgroundTintList
        btn2Players.backgroundTintList = if (selectedPlayerCount == 2) colorSelected else colorUnselected
        btn3Players.backgroundTintList = if (selectedPlayerCount == 3) colorSelected else colorUnselected
        btn4Players.backgroundTintList = if (selectedPlayerCount == 4) colorSelected else colorUnselected

        btnIndividualMode.backgroundTintList = if (!isDuoMode) colorIndividual else colorUnselected
        btnDuoMode.backgroundTintList = if (isDuoMode) colorIndividual else colorUnselected

        // Block interaction and dim Duo Mode if player count isn't 4
        if (selectedPlayerCount == 4) {
            btnDuoMode.alpha = 1.0f
            btnDuoMode.isEnabled = true
        } else {
            btnDuoMode.alpha = 0.4f
            btnDuoMode.isEnabled = false
        }

        val modeLabel = if (isDuoMode) "Duo (Teams)" else "Individual"
        tvSelectionSummary.text = "$selectedPlayerCount Players • $modeLabel"
    }
}