package com.example.todo.service

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe

object LLMCredentialStore {
    private const val SERVICE_NAME = "GitAIAssistant.LLM"

    fun getApiKey(configId: String): String {
        if (configId.isBlank()) return ""
        return PasswordSafe.instance.getPassword(attributes(configId)).orEmpty()
    }

    fun setApiKey(configId: String, apiKey: String) {
        if (configId.isBlank()) return
        val credentials = apiKey.takeIf { it.isNotBlank() }?.let { Credentials(configId, it) }
        PasswordSafe.instance.set(attributes(configId), credentials)
    }

    fun removeApiKey(configId: String) {
        setApiKey(configId, "")
    }

    private fun attributes(configId: String): CredentialAttributes {
        return CredentialAttributes("$SERVICE_NAME.$configId")
    }
}
