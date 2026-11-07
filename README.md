# seokkeobom-api

Treeplan 백엔드 (Spring Boot 3.5, Java 21). 프론트엔드는 별도 레포로 분리되어 있다.

## 로컬 실행

1. PostgreSQL 준비 (기본값: `localhost:5433/seokkeobom`, 계정 `seokkeobom` / `seokkeobom`)
2. 레포 최상위에 `.env` 를 두면 자동으로 읽는다 (git 제외). 환경변수가 있으면 그쪽이 우선.

   ```properties
   DB_URL=jdbc:postgresql://localhost:5433/seokkeobom
   DB_USERNAME=seokkeobom
   DB_PASSWORD=seokkeobom
   JWT_SECRET=32바이트-이상의-비밀값
   GEMINI_API_KEY=        # 비우면 예시(mock) 모드
   CORS_ORIGINS=http://localhost:5173
   ```

3. 실행

   ```bash
   ./gradlew bootRun
   ```

   API 는 `http://localhost:8080/api` 아래에 열린다. 상태 확인: `GET /api/actuator/health`

## 테스트

```bash
./gradlew test
```

## 배포

`main` 에 push 하면 GitHub Actions (`.github/workflows/deploy.yml`) 가 테스트 후 이미지를 ECR 에 올린다.
