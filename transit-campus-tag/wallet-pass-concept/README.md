# Concept Wallet Pass

A visual mock-up of the unified transit + campus card, installable in
Apple Wallet — for showing the idea to lecturers, not for real use.

**This is not the NFC prototype.** Tapping this pass does nothing; there's
no NFC behaviour in it at all, because Wallet only allows real NFC-driven
passes through Apple's closed Express Transit / campus-badge partner
programs (see the top-level README). This is a "store card" style pass —
balance, holder name, tag/campus status fields, a QR code, and a back
page explaining the project — that sits in Wallet looking like the real
thing, so it's something to point at and click into during a pitch. The
actual working tap logic lives in `../app` (Android) and `../ios`
(iOS reader).

## What's here

```
pass-source/     pass.json + all the images (icon, logo, strip) — the
                 editable source of the pass.
build-pass.sh    Packages pass-source/ into a signed TagDemoConcept.pkpass.
```

The packaging script (manifest generation, PKCS#7 signing, zip layout) has
been run and verified in this project against a throwaway test
certificate — the mechanics work. It has not been run with a real Apple
certificate, since that requires your own paid developer account.

## What you need before building

Apple Wallet refuses to install a pass unless it's signed by a real
**Pass Type ID certificate**, which needs a **paid Apple Developer
Program membership** ($99/year — a free account can't issue this
certificate):

1. At [developer.apple.com](https://developer.apple.com) → Certificates,
   Identifiers & Profiles → Identifiers → **+**, register a new **Pass
   Type ID** (e.g. `pass.nz.kaliscope.tagdemo.concept`).
2. Open that identifier, create a certificate for it, download it, and
   double-click to install into Keychain Access.
3. In Keychain Access, find the certificate, right-click → **Export**, and
   save as `PassCert.p12` (set a password, you'll need it below).
4. Convert to PEM (run on a Mac, or anywhere with OpenSSL):
   ```
   openssl pkcs12 -in PassCert.p12 -clcerts -nokeys -out passcert.pem
   openssl pkcs12 -in PassCert.p12 -nocerts -nodes -out passkey.pem
   ```
5. Download Apple's WWDR G4 intermediate certificate from
   [Apple PKI](https://www.apple.com/certificateauthority/) and convert it
   the same way (or convert its `.cer` to `.pem` with
   `openssl x509 -inform DER -in AppleWWDRCAG4.cer -out wwdr.pem`).
6. Edit `pass-source/pass.json`: replace `teamIdentifier` with your real
   10-character Apple Developer Team ID, and `passTypeIdentifier` with the
   identifier you registered in step 1.

## Build and install

```
./build-pass.sh passcert.pem passkey.pem wwdr.pem
```

This produces `build/TagDemoConcept.pkpass`. To install it on an iPhone:
AirDrop the file to it (opens directly in Wallet), or email it to
yourself and tap it, or serve it from a web server with the
`application/vnd.apple.pkpass` content type and open the link on the
phone.
