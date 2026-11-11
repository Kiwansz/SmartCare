package com.example.smartcare

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import com.example.smartcare.staff.StaffFeature
import com.google.android.material.button.MaterialButton

/**
 * One shared placeholder for every staff feature that another module will build.
 * Names the feature and, when the registry knows it, the module owner.
 */
class ComingSoonActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_coming_soon)
        setupHeader(R.string.coming_soon, R.string.coming_soon_sub)

        val feature = intent.getStringExtra(EXTRA_FEATURE)
            ?.let { name -> StaffFeature.entries.firstOrNull { it.name == name } }
        if (feature == null) { finish(); return }

        val title = getString(feature.title)
        findViewById<ImageView>(R.id.ivFeature).setImageResource(feature.icon)
        findViewById<TextView>(R.id.tvFeature).text = title
        findViewById<TextView>(R.id.tvBody).text = getString(R.string.coming_soon_body, title)
        findViewById<TextView>(R.id.tvOwner).text = feature.owner
            ?.let { getString(R.string.coming_soon_owner, it) }
            ?: getString(R.string.coming_soon_other_module)
        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener { finish() }
    }

    companion object {
        private const val EXTRA_FEATURE = "feature"

        /** Opens the feature's real screen if it has one, otherwise this placeholder. */
        fun open(context: Context, feature: StaffFeature) {
            val target = feature.screen ?: ComingSoonActivity::class.java
            context.startActivity(Intent(context, target).putExtra(EXTRA_FEATURE, feature.name))
        }
    }
}
