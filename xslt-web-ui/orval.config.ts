import { defineConfig } from "orval";

export default defineConfig({
  xsltService: {
    input: {
      target: "http://localhost:8080/v3/api-docs",
    },
    output: {
      target: "src/api/generated/endpoints.ts",
      schemas: "src/api/generated/models",
      client: "react-query",
      mode: "tags-split",
      clean: true,
      override: {
        mutator: {
          path: "src/api/axios-instance.ts",
          name: "customInstance",
        },
      },
    },
  },
});
