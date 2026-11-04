package com.example.smartcare.data

import com.example.smartcare.BuildConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/** Single shared Supabase client for the whole app. */
object SupabaseProvider {

    @OptIn(io.github.jan.supabase.annotations.SupabaseInternal::class) // for httpConfig {}
    val client by lazy {
        require(BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()) {
            "Set supabase.url and supabase.anon.key in local.properties"
        }
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Auth)
            install(Postgrest)

            // Debug builds: log each request's method + URL + status to Logcat (tag "SupabaseHttp").
            // INFO level never includes headers or bodies, so tokens/passwords are not logged.
            // IC numbers can appear in query URLs, so they are masked before logging.
            if (BuildConfig.DEBUG) {
                httpConfig {
                    install(io.ktor.client.plugins.logging.Logging) {
                        logger = object : io.ktor.client.plugins.logging.Logger {
                            override fun log(message: String) {
                                android.util.Log.d("SupabaseHttp", maskSensitive(message))
                            }
                        }
                        level = io.ktor.client.plugins.logging.LogLevel.INFO
                    }
                }
            }
        }
    }

    private val IC_IN_URL = Regex("""(ic_number=[a-z]+\.)[^&\s]+""")
    private val LONG_DIGITS = Regex("""\d{12}""")

    /** Hides IC numbers (ic_number=eq.123456789012 and any bare 12-digit run) in a log line. */
    internal fun maskSensitive(message: String): String =
        message.replace(IC_IN_URL, "$1***").replace(LONG_DIGITS, "************")
}
