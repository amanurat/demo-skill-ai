# Prompt: สร้างระบบ AI Multi-Agent สำหรับพัฒนาซอฟต์แวร์แบบ End-to-End ตาม SDLC

> **Journey Note:** ไฟล์นี้คือ prompt ที่ revise แล้วจาก `requirement.md` (ฉบับ raw)
> เพื่อใช้เป็น input ในการสร้างโปรเจกต์ด้วย AI Agent

---

## 🎯 Objective
ออกแบบและสร้างโปรเจกต์ที่ใช้ **AI Multi-Agent System** ทำงานแบบอัตโนมัติครอบคลุมตลอด **Software Development Life Cycle (SDLC)** ตั้งแต่ Discovery → Planning → Design → Development → Testing → Deployment โดยไม่มีรอยต่อ (seamless collaboration)

## 📋 Project Context
- **Domain:** ระบบ Banking (จำลอง use case จริง)
- **Architecture:** Full-stack application
  - **Frontend:** Angular (เป็นหลัก)
  - **Backend:** Spring Boot Microservices (Java)
- **Goal:** สร้าง AI Agents ที่ทำงานร่วมกันเหมือนทีมพัฒนาซอฟต์แวร์จริง

## 🤖 AI Agents Architecture

### 1. Orchestrator Agent (Player / Manager)
- ทำหน้าที่เป็น **Project Manager** วางแผนและกระจายงาน
- รับ requirement → แตก task → มอบหมายให้ agent ที่เหมาะสม
- ติดตามสถานะและตรวจสอบคุณภาพงานก่อนส่งต่อ

### 2. Specialized Agents

| Agent | Role | Responsibility |
|---|---|---|
| **BA Agent** | Business Analyst | วิเคราะห์ requirement, เขียน user story, acceptance criteria |
| **Solution Architect Agent** | Architect | ออกแบบ system architecture, เลือก tech stack |
| **Tech Lead Agent** | Tech Lead | ออกแบบ technical design, API contract, database schema |
| **Frontend Dev Agent** | Angular Developer | พัฒนา UI components ด้วย Angular |
| **Backend Dev Agent** | Spring Boot Developer | พัฒนา microservices ด้วย Java/Spring Boot |
| **QA Agent** | Quality Assurance | เขียน test case, unit test, integration test, E2E test |
| **DevOps Agent** | DevOps Engineer | จัดการ CI/CD, deployment, infrastructure |

## 📦 Deliverables ที่ต้องการ

1. **Project Structure Design**
   - โครงสร้างโฟลเดอร์ทั้ง frontend และ backend
   - การจัดการ monorepo / multi-repo

2. **Agent Skill Files (`*.skill.md`)** สำหรับแต่ละ agent โดยเฉพาะ:
   - **Backend Java/Spring Boot Skill** พร้อม best practices:
     - Clean Architecture / Hexagonal Architecture
     - SOLID principles
     - DTO, Mapper patterns
     - Exception handling
     - Security (Spring Security, JWT/OAuth2)
     - Database (JPA, Flyway/Liquibase)
     - Testing (JUnit 5, Mockito, Testcontainers)
     - Observability (logging, metrics, tracing)
     - API documentation (OpenAPI/Swagger)
   - **Frontend Angular Skill** พร้อม best practices
   - **Skill อื่นๆ** สำหรับแต่ละ agent

3. **Workflow & Communication Protocol**
   - วิธีที่ agents สื่อสารและส่งต่องานกัน
   - Format ของ artifact ที่ส่งต่อระหว่าง agent (เช่น JSON schema)
   - Quality gate ระหว่างแต่ละ phase

4. **Banking Use Case ตัวอย่าง**
   - เลือก feature เช่น Account Opening, Money Transfer, Loan Application
   - แสดงตัวอย่าง flow ตั้งแต่ requirement จนถึง deployed code

## ✅ Acceptance Criteria
- ระบบทำงานแบบ autonomous (มนุษย์เป็น reviewer/approver เท่านั้น)
- Agent แต่ละตัวมี skill file ชัดเจน reusable
- มี best practices ฝังอยู่ใน skill ของแต่ละ role
- Output code มีคุณภาพ production-ready

## 📤 Expected Output
1. เอกสาร architecture overview
2. โครงสร้างโปรเจกต์ (folder tree)
3. ไฟล์ `skill.md` ของแต่ละ agent
4. ตัวอย่าง end-to-end workflow ด้วย banking feature

---

## 📝 Journey Log

- **2026-05-17** — Revised raw requirement (`requirement.md`) → structured prompt
–––––ชช