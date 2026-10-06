import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

afterEach(() => {
  cleanup();
  // The guide-sample test runs in the node environment, which has no storage.
  if (typeof localStorage !== 'undefined') localStorage.clear();
});
