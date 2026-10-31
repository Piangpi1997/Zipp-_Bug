# AI Providers

Zip_Bug supports provider-neutral AI access through a front-end selector.

Current providers:
- OpenAI
- Google Gemini
- Anthropic Claude

Users enter their own API keys in Settings. Keys are encrypted with Android Keystore and are never committed to Git.

The UI must not display success unless the selected provider returns a successful HTTP response.
