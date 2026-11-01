package com.example.smartcare.data

import com.example.smartcare.BuildConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/** Single shared Supabase client for the whole app. */
object SupabaseProvider {

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
        }
    }

    /**
     * Creates an account in Supabase Auth. Name, IC number and phone are stored as user metadata.
     * If email confirmation is enabled in the project, the user must confirm before logging in.
     */
    suspend fun signUp(
        fullName: String,
        icNumber: String,
        email: String,
        phone: String,
        password: String
    ): Result<Unit> = runCatching {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
            data = buildJsonObject {
                put("full_name", fullName)
                put("ic_number", icNumber)
                put("phone", phone)
            }
        }

        // Insert the clinic rows: user_account first, then the linked patient row.
        val account = client.postgrest.from("user_account").insert(
            buildJsonObject {
                put("email", email)
                put("ic_number", icNumber)
                put("phone_number", phone)
                put("role", "PATIENT")
            }
        ) { select() }.decodeSingle<JsonObject>()

        val userId = account.getValue("user_id").jsonPrimitive.int
        client.postgrest.from("patient").insert(
            buildJsonObject {
                put("user_id", userId)
                put("full_name", fullName)
            }
        )
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
