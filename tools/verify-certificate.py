"""Check certificate identity AFTER apksigner verify has successfully verified the APK signature."""
from pathlib import Path
import re
import sys


def verify_certificate(report: str, expected: str) -> str:
    expected = expected.strip().lower()
    if not re.fullmatch(r"[0-9a-f]{64}", expected):
        raise ValueError("Expected certificate fingerprint must be exactly 64 hexadecimal digits.")
    # Build tools may print "Signer #1 certificate..." or "V2 Signer: certificate...".
    # Only certificate digests count; a public-key digest is a different value.
    fingerprints = {
        match.lower() for match in re.findall(
            r"^.*\bcertificate SHA-256 digest:\s*([0-9a-fA-F]{64})\s*$", report, re.MULTILINE
        )
    }
    if fingerprints != {expected}:
        raise ValueError("APK certificate is missing or differs from the permanent distribution certificate.")
    return expected


if __name__ == "__main__":
    fingerprint = verify_certificate(Path(sys.argv[1]).read_text(encoding="utf-8"),
                                     Path(sys.argv[2]).read_text(encoding="ascii"))
    print(f"Permanent certificate verified: {fingerprint}")
