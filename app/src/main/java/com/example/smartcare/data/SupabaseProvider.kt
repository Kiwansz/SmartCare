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
            if (BuildConfig.DEBUG) {
                httpConfig {
                    install(io.ktor.client.plugins.logging.Logging) {
                        logger = object : io.ktor.client.plugins.logging.Logger {
                            override fun log(message: String) {
                                android.util.Log.d("SupabaseHttp", message)
                            }
                        }
                        level = io.ktor.client.plugins.logging.LogLevel.INFO
                    }
                }
            }
        }
    }

    /**
     * Pings the Supabase Auth health endpoint through the client's HTTP stack.
     * Succeeds only if the URL is reachable and the API key is accepted.
     */
    suspend fun testConnection(): Result<String> = runCatching {
        val response = client.httpClient.request("${BuildConfig.SUPABASE_URL}/auth/v1/health") {
            method = io.ktor.http.HttpMethod.Get
        }
        val status = response.status.value
        check(status in 200..299) { "Supabase responded with HTTP $status" }
        "Connected to Supabase (HTTP $status)"
    }
}
