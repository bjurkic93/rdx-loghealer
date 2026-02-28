# LogHealer Roadmap

## Planned Features

### Audit Log Support
- **Priority**: High
- **Description**: Proširiti LogHealer za primanje audit logova iz aplikacija
- **Funkcionalnosti**:
  - Novi log type: `AUDIT` uz postojeće `INFO`, `WARN`, `ERROR`, `EXCEPTION`
  - Poseban Elasticsearch index: `loghealer-audit-*`
  - Strukturirana polja:
    - `actor` - tko je izvršio akciju (user ID, email)
    - `action` - tip akcije (LOGIN, LOGOUT, CREATE, UPDATE, DELETE, ACCESS)
    - `resource` - tip resursa (User, Order, Payment, etc.)
    - `resourceId` - ID resursa
    - `oldValue` - stara vrijednost (za UPDATE)
    - `newValue` - nova vrijednost (za UPDATE)
    - `ipAddress` - IP adresa
    - `userAgent` - browser/client info
  - Dashboard UI za pregled audit logova s filterima
  - Integracija kroz `loghealer-spring-boot-starter` library
- **Use cases**:
  - Praćenje login/logout aktivnosti
  - CRUD operacije na entitetima
  - Admin akcije
  - Pristup osjetljivim podacima
  - Compliance (GDPR, SOC2)

---

### Distributed Tracing & Service Groups
- **Priority**: High
- **Description**: Grupirati GitHub repozitorije i baze u "sustave" za end-to-end tracing
- **Funkcionalnosti**:
  - **Service Group** entitet:
    - Naziv grupe (npr. "RdX School Platform")
    - Lista povezanih projekata/repozitorija
    - Lista povezanih baza podataka
    - Service topology/dependency map
  - **Trace Correlation**:
    - `traceId` propagacija kroz sve servise (HTTP headers: `X-Trace-Id`)
    - Automatsko povezivanje logova iz različitih servisa po `traceId`
    - Timeline view - kronološki prikaz requesta kroz servise
  - **End-to-End Analysis**:
    - AI analiza s kontekstom cijelog flowa (ne samo jednog servisa)
    - Prepoznavanje gdje je root cause (koji servis, koja linija)
    - Fix suggestion s referencama na ispravne repozitorije
  - **Database Context**:
    - Povezati slow query logove s aplikacijskim logovima
    - Schema awareness za AI fix suggestions
- **UI**:
  - Service group management (CRUD)
  - Trace explorer - waterfall view requesta
  - Cross-service exception grouping
- **Library changes** (`loghealer-spring-boot-starter`):
  - Auto-propagacija `traceId` kroz HTTP clients (RestTemplate, WebClient, Feign)
  - MDC integration za `traceId`
  - Database query logging s `traceId`

---

### Future Ideas
- [ ] Alerting/notifications (email, Slack, webhook)
- [ ] Log retention policies po projektu
- [ ] Dashboard customization per user
- [ ] Multi-tenant billing
- [ ] Log export (CSV, JSON)
- [ ] Saved searches / filters
