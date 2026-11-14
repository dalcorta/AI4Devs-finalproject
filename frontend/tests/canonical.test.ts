import { describe, expect, it } from "vitest";
import vectores from "../../shared/test-vectors/canonical-hash.json";
import { canonicalizar, hashCanonico } from "@/lib/canonical";

// Los mismos vectores que usa CanonicalHasherTest en el backend: si alguno cambia, cambia en los dos lados.
describe("hash canónico (RFC 8785 + keccak256), vectores compartidos con el backend", () => {
  it("hay vectores para probar", () => {
    expect(vectores.vectors.length).toBeGreaterThan(0);
  });

  for (const vector of vectores.vectors) {
    it(`${vector.name}: forma canónica`, () => {
      expect(canonicalizar(vector.input)).toBe(vector.canonical);
    });

    it(`${vector.name}: keccak256`, () => {
      expect(hashCanonico(vector.input)).toBe(vector.keccak256);
    });
  }

  it("un texto que no es JSON es un error, no un hash", () => {
    expect(() => hashCanonico("{no es json")).toThrow();
  });
});
