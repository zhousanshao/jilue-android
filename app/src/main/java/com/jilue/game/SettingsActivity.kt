package com.jilue.game

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jilue.game.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setTitle(R.string.settings_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val prefs = getSharedPreferences(Constants.PREFS, MODE_PRIVATE)
        binding.ownerEdit.setText(prefs.getString("owner", Constants.DEFAULT_OWNER))
        binding.repoEdit.setText(prefs.getString("repo", Constants.DEFAULT_REPO))
        binding.baseUrlEdit.setText(prefs.getString("baseUrl", ""))

        binding.saveBtn.setOnClickListener {
            prefs.edit()
                .putString("owner", binding.ownerEdit.text.toString().trim())
                .putString("repo", binding.repoEdit.text.toString().trim())
                .putString("baseUrl", binding.baseUrlEdit.text.toString().trim())
                .apply()
            Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.clearBtn.setOnClickListener {
            ResourceManager.clearCache(this)
            Toast.makeText(this, R.string.settings_cleared, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
