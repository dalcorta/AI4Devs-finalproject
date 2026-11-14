import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

// Sin los globals de Vitest, Testing Library no limpia el DOM entre tests por su cuenta.
afterEach(cleanup);
