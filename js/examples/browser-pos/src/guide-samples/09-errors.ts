// Samples for "Errors" and "Customer prompts".
import {
  EngineError,
  EngineOutdatedError,
  EngineUnavailableError,
  SessionError,
  type TerminalShopperSession,
} from '@bilt/pos-sdk';

declare function showTerminalProblem(message: string, nexoErrorCondition: string | undefined): void;

export async function confirmReceipt(session: TerminalShopperSession): Promise<boolean> {
  try {
    return await session.requestConfirmation('Would you like a receipt?');
  } catch (error) {
    if (!(error instanceof SessionError)) throw error;
    switch (error.code) {
      case 'CANCELLED': // the shopper backed out on the terminal
      case 'ABORTED': // the register called abort()
      case 'TIMEOUT':
        return false;
      case 'NETWORK':
      case 'TERMINAL_ERROR':
        showTerminalProblem(error.message, error.nexoErrorCondition);
        return false;
      default:
        throw error;
    }
  }
}

export function classify(error: unknown): string {
  if (error instanceof SessionError) return `session failure ${error.code}`;
  if (error instanceof EngineOutdatedError) return `host speaks ${error.available.join(', ')}`;
  if (error instanceof EngineUnavailableError) return 'no host answered';
  if (error instanceof EngineError) return 'engine failure';
  return 'something else';
}
