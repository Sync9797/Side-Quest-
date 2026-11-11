package com.sidequest.widgetmaker.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.sidequest.widgetmaker.spec.DesignParser
import com.sidequest.widgetmaker.spec.WidgetDesign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Asks Claude to design a widget from a description, or to change an existing design.
 *
 * The key is the user's own Anthropic API key, kept on the phone. Before
 * publishing the app to other people, move this call behind a small server of
 * your own so the key never ships in (or with) the app.
 */
class WidgetDesigner(apiKey: String) {
    private val client: AnthropicClient = AnthropicOkHttpClient.builder().apiKey(apiKey).build()

    class DesignFailed(message: String) : Exception(message)

    /** A new design from [description], or [previous] changed as [description] asks. */
    suspend fun design(description: String, previous: WidgetDesign? = null): WidgetDesign =
        withContext(Dispatchers.IO) {
            val request = if (previous == null) {
                "Design this widget: $description"
            } else {
                "Here is the current widget design:\n" + DesignParser.encode(previous) +
                    "\n\nChange it like this, keeping everything else: $description"
            }
            val first = ask(request, retry = null)
            try {
                DesignParser.parse(first)
            } catch (e: DesignParser.InvalidDesign) {
                // One retry, telling Claude what was wrong.
                val second = ask(request, retry = first to e.message.orEmpty())
                try {
                    DesignParser.parse(second)
                } catch (e2: DesignParser.InvalidDesign) {
                    throw DesignFailed("Claude's design couldn't be used (${e2.message}). Try rewording it.")
                }
            }
        }

    private fun ask(request: String, retry: Pair<String, String>?): String {
        val params = MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(8000L)
            .system(Prompts.SYSTEM)
            // A widget layout is a small, well-specified job: medium effort is quick and plenty.
            .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.MEDIUM).build())
            // If a request is declined by a safety check, let the API retry it on a fallback model.
            .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
            .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
            .addUserMessage(request)
            .apply {
                if (retry != null) {
                    addAssistantMessage(retry.first)
                    addUserMessage(
                        "That design couldn't be used: ${retry.second}\n" +
                            "Reply again with only the corrected JSON object.",
                    )
                }
            }
            .build()

        val response: Message = try {
            client.messages().create(params)
        } catch (e: UnauthorizedException) {
            throw DesignFailed("Your API key wasn't accepted. Check it in Settings.")
        } catch (e: PermissionDeniedException) {
            throw DesignFailed("Your API key isn't allowed to use this model.")
        } catch (e: RateLimitException) {
            throw DesignFailed("Too many requests right now. Wait a moment and try again.")
        } catch (e: AnthropicServiceException) {
            throw DesignFailed("Claude had a problem (${e.statusCode()}). Try again in a moment.")
        } catch (e: AnthropicIoException) {
            throw DesignFailed("Couldn't reach Claude. Check your internet connection.")
        }

        if (response.stopReason().orElse(null) == StopReason.REFUSAL) {
            throw DesignFailed("Claude declined to design that widget. Try describing it differently.")
        }
        return response.content()
            .mapNotNull { block -> block.text().orElse(null)?.text() }
            .joinToString("\n")
    }

    companion object {
        const val MODEL = "claude-opus-5-5"
    }
}
