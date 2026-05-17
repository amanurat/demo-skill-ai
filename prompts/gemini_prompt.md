# Gemini_prompt.md

**Role:** 
Act as a Master AI Orchestrator and Senior Enterprise Software Architect.

**Context:** 
I am building a fully automated Agentic AI workflow that simulates a complete End-to-End Software Development Life Cycle (SDLC). The goal is to develop an Enterprise Banking Application. 
The technology stack is:
- **Frontend:** Angular
- **Backend:** Java Spring Boot Microservices

**Objective:** 
Design a seamless, fully automated multi-agent system where an "Orchestrator Agent" acts as the Project Manager. This Orchestrator will plan, delegate, and manage tasks across various specialized AI Agents (e.g., BA, Solution Architect/Tech Lead, Frontend Dev, Backend Dev, QA, and DevOps) covering all SDLC phases: Discovery, Planning, Design, Development, Testing, and Deployment.

**Tasks Required:**

### 1. Agentic Workflow & Project Structure
Design the architecture of how these AI Agents will collaborate seamlessly without human intervention. 
- Define the input/output handoffs between agents (e.g., how the BA passes specs to the Tech Lead, and how the Tech Lead passes the architecture to the Devs).
- Explain the feedback loops (e.g., what happens when QA finds a bug and sends it back to the Dev).

### 2. Agent Roles & Personas
Briefly define the persona and exact responsibilities of each agent in this specific Banking project workflow.

### 3. Generate `ai_skill.md` (Backend Focus)
Create the complete content for an `ai_skill.md` file specifically designed to instruct the **Backend Java Spring Boot Developer Agent**. This output must be a Markdown code block ready to be saved. It must include:
- Core responsibilities for building banking microservices.
- Strict coding standards and Enterprise Best Practices (Clean Code, SOLID principles, Design Patterns).
- Security practices for banking (OAuth2, JWT, Data Encryption, OWASP top 10).
- Microservices best practices (API Gateway, Service Discovery, Event-driven architecture with Kafka/RabbitMQ).
- Testing requirements (JUnit, Mockito, Integration Testing).

**Output Format:**
Please provide a well-structured response using Markdown. Use clear headings, bullet points, and ensure the `ai_skill.md` content is placed inside a distinct markdown code block. (คุณสามารถอธิบายกระบวนการทั้งหมดเป็นภาษาไทยได้ แต่ส่วนของโค้ดและ `ai_skill.md` ให้คงไว้เป็นภาษาอังกฤษเพื่อความแม่นยำทางเทคนิค)