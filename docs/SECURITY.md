# Security Model

1. ZIP extraction resolves canonical paths and rejects path traversal.
2. ZIP imports have file-count and extracted-size caps.
3. Imported web apps run in a sandboxed localhost runtime.
4. No imported app gets an unrestricted JavaScript-to-shell bridge.
5. Termux execution is allow-listed by binary and validated arguments.
6. API keys are encrypted using Android Keystore.
7. Signing keys and secrets are never committed.
8. Build/install/certification labels must reflect real tool results only.
9. Social/media features should only process content the user is authorized to access and must respect platform terms and copyright.
