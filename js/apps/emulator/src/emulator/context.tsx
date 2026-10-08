import { createContext, useContext, useSyncExternalStore, type ReactNode } from 'react';
import type { EmulatorController, EmulatorState } from './state';

const ControllerContext = createContext<EmulatorController | null>(null);
ControllerContext.displayName = 'EmulatorController';

export function EmulatorProvider({
  controller,
  children,
}: {
  controller: EmulatorController;
  children?: ReactNode;
}): ReactNode {
  return <ControllerContext.Provider value={controller}>{children}</ControllerContext.Provider>;
}

export function useController(): EmulatorController {
  const controller = useContext(ControllerContext);
  if (!controller) throw new Error('useController() needs an <EmulatorProvider> above it');
  return controller;
}

/** The controller's state, re-rendered on every change: the desktop's `collectAsState()`. */
export function useEmulatorState(): EmulatorState {
  const controller = useController();
  return useSyncExternalStore(controller.subscribe, controller.getState, controller.getState);
}
