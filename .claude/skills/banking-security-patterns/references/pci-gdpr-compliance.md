# Compliance — PCI-DSS, GDPR, PDPA, BoT, AML

Reference loaded on demand by `banking-security-patterns` skill. Use during scope assessment and before a service is promoted to production.

## Severity Legend
- **blocker** — compliance violation; cannot ship
- **major** — must remediate before next audit window
- **minor** — improvement opportunity

---

## PCI-DSS Checklist

Applies if the service stores, processes, or transmits cardholder data (PAN, CVV, track data, expiry).

- [ ] **Scope check** — Does any code touch card PAN? If yes, ensure tokenization vendor is used; this service should NOT store PAN.
- [ ] **Network segmentation** — Services in the Cardholder Data Environment (CDE) are clearly marked; non-CDE services cannot reach CDE except via documented gateways.
- [ ] **Logging** — Do not log full PAN, CVV, or track data anywhere (app logs, access logs, error responses, support tools).
- [ ] **Key management** — Encryption keys are vault-stored, rotated on schedule, and access is audited.
- [ ] **Audit trail** — Retained ≥ 1 year online, ≥ 3 years accessible.

---

## GDPR Checklist

Applies if the service processes personal data of EU data subjects (and equivalent local laws — see PDPA below).

- [ ] **Data minimization** — Only collect what's needed for the documented purpose.
- [ ] **Lawful basis** — Documented per data category (contract, consent, legitimate interest, legal obligation).
- [ ] **Subject rights** — Export endpoint (Art 15 access / Art 20 portability) and erasure endpoint (Art 17) exist and are tested.
- [ ] **Retention** — Per-data-type retention enforced (audit data may override for legal obligation).
- [ ] **Cross-border transfer** — Documented; SCCs or adequacy decision in place if data leaves jurisdiction.

---

## Banking-Local Regulations

Markers — confirm with compliance team in real engagement.

### Bank of Thailand (BoT) IT Regulations
- [ ] BoT IT risk-management notice — change-management process documented and followed
- [ ] Incident reporting workflow defined (timing + recipient)
- [ ] Outsourcing register kept current for cloud / SaaS dependencies

### Personal Data Protection Act (PDPA, Thailand)
- [ ] DPO appointed and contactable
- [ ] Consent records captured with timestamp + version of notice shown
- [ ] Data subject rights (access, correction, withdrawal of consent) supported
- [ ] Cross-border transfer disclosed in privacy notice

### AML / KYC Reporting
- [ ] KYC profile attached to every customer entity (level + verification source)
- [ ] Suspicious-transaction monitoring hook present (rules engine or stream processor)
- [ ] Threshold / structuring detection rules in place
- [ ] Reporting workflow to AMLO (or local FIU) documented and tested
