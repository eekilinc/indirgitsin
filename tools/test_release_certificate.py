import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("verify_certificate", Path(__file__).with_name("verify-certificate.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class CertificateVerificationTest(unittest.TestCase):
    expected = "4cdb15174ada39cff8a7f8e34947c2c8ad47a45c0f7809ec14b1692d71fc4027"

    def test_old_and_new_apksigner_formats(self):
        for prefix in ("Signer #1", "V2 Signer:", "V3 Signer:"):
            with self.subTest(prefix=prefix):
                report = f"{prefix} certificate SHA-256 digest: {self.expected.upper()}\r\n"
                self.assertEqual(self.expected, module.verify_certificate(report, self.expected + "\n"))

    def test_repeated_same_certificate_across_signature_schemes(self):
        report = "\n".join(f"V{i} Signer: certificate SHA-256 digest: {self.expected}" for i in (2, 3))
        self.assertEqual(self.expected, module.verify_certificate(report, self.expected))

    def test_different_certificate_is_rejected(self):
        with self.assertRaises(ValueError):
            module.verify_certificate("V2 Signer: certificate SHA-256 digest: " + "f" * 64, self.expected)

    def test_public_key_hash_cannot_be_mistaken_for_certificate(self):
        with self.assertRaises(ValueError):
            module.verify_certificate("V2 Signer: public key SHA-256 digest: " + self.expected, self.expected)

    def test_missing_or_extra_signer_is_rejected(self):
        for report in ("", "V2 Signer: certificate SHA-256 digest: " + self.expected +
                       "\nSigner #2 certificate SHA-256 digest: " + "f" * 64):
            with self.assertRaises(ValueError):
                module.verify_certificate(report, self.expected)


if __name__ == "__main__":
    unittest.main()
