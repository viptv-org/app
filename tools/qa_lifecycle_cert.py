#!/usr/bin/env python3
"""Issue a disposable loopback fixture leaf using the existing local QA CA."""
import argparse
from datetime import datetime, timedelta, timezone
import ipaddress
from pathlib import Path
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import NameOID, ExtendedKeyUsageOID

parser = argparse.ArgumentParser()
parser.add_argument("--root", type=Path, required=True)
parser.add_argument("--ca-cert", type=Path, required=True)
parser.add_argument("--ca-key", type=Path, required=True)
args = parser.parse_args()
args.root.mkdir(mode=0o700, parents=True, exist_ok=True)
ca = x509.load_pem_x509_certificate(args.ca_cert.read_bytes())
ca_key = serialization.load_pem_private_key(args.ca_key.read_bytes(), password=None)
key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
now = datetime.now(timezone.utc)
leaf = (x509.CertificateBuilder().subject_name(x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "VIPTV synthetic lifecycle fixture")]))
        .issuer_name(ca.subject).public_key(key.public_key()).serial_number(x509.random_serial_number())
        .not_valid_before(now - timedelta(minutes=5)).not_valid_after(now + timedelta(days=2))
        .add_extension(x509.BasicConstraints(ca=False, path_length=None), critical=True)
        .add_extension(x509.SubjectKeyIdentifier.from_public_key(key.public_key()), critical=False)
        .add_extension(x509.AuthorityKeyIdentifier.from_issuer_public_key(ca_key.public_key()), critical=False)
        .add_extension(x509.ExtendedKeyUsage([ExtendedKeyUsageOID.SERVER_AUTH]), critical=False)
        .add_extension(x509.KeyUsage(digital_signature=True, content_commitment=False, key_encipherment=True,
                                     data_encipherment=False, key_agreement=False, key_cert_sign=False, crl_sign=False,
                                     encipher_only=None, decipher_only=None), critical=True)
        .add_extension(x509.SubjectAlternativeName([x509.IPAddress(ipaddress.ip_address("127.0.0.1")),
                                                   x509.IPAddress(ipaddress.ip_address("10.0.2.2"))]), critical=False)
        .sign(ca_key, hashes.SHA256()))
(args.root / "fixture.crt").write_bytes(leaf.public_bytes(serialization.Encoding.PEM))
private = args.root / "fixture.key"
private.write_bytes(key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
private.chmod(0o600)
print("Disposable loopback fixture certificate issued; existing CA files unchanged.")
