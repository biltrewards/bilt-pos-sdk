import { useEffect, useRef, type ReactNode } from 'react';

/** A Material-style tab row: buttons with `role="tab"`, the selected one underlined. */
export function TabRow<T extends string>({
  tabs,
  selected,
  onSelect,
  label,
  className,
}: {
  tabs: readonly { readonly id: T; readonly label: string }[];
  selected: T;
  onSelect: (id: T) => void;
  label: string;
  className?: string;
}): ReactNode {
  return (
    <div className={`tab-row ${className ?? ''}`} role="tablist" aria-label={label}>
      {tabs.map((tab) => (
        <button
          key={tab.id}
          type="button"
          role="tab"
          aria-selected={tab.id === selected}
          className={tab.id === selected ? 'tab active' : 'tab'}
          onClick={() => onSelect(tab.id)}
        >
          {tab.label}
        </button>
      ))}
    </div>
  );
}

export function LabeledCheckbox({
  label,
  checked,
  disabled,
  onChange,
}: {
  label: string;
  checked: boolean;
  disabled?: boolean;
  onChange: (checked: boolean) => void;
}): ReactNode {
  return (
    <label className="check">
      <input
        type="checkbox"
        checked={checked}
        disabled={disabled}
        onChange={(event) => onChange(event.target.checked)}
      />
      {label}
    </label>
  );
}

export function LabeledRadio({
  label,
  selected,
  enabled,
  onClick,
  name,
}: {
  label: string;
  selected: boolean;
  enabled: boolean;
  onClick: () => void;
  name: string;
}): ReactNode {
  return (
    <label className="check">
      <input type="radio" name={name} checked={selected} disabled={!enabled} onChange={onClick} />
      {label}
    </label>
  );
}

/** One cart line: optional leading control, "qty× description", the amount, optional trailing controls. */
export function LineItemRow({
  quantity,
  description,
  amountLabel,
  leading,
  trailing,
  className,
  onClick,
}: {
  quantity: number;
  description: string;
  amountLabel: string;
  leading?: ReactNode;
  trailing?: ReactNode;
  className?: string;
  onClick?: () => void;
}): ReactNode {
  return (
    <li className={`line-item ${className ?? ''}`}>
      {leading}
      {onClick ? (
        <button type="button" className="line-text clickable" onClick={onClick}>
          {quantity}× {description}
        </button>
      ) : (
        <span className="line-text">
          {quantity}× {description}
        </span>
      )}
      <span className="line-amount">{amountLabel}</span>
      {trailing}
    </li>
  );
}

/**
 * A modal dialog over the page, as Compose's `AlertDialog`: a title, a body and a button row.
 * `onDismiss` runs on Escape and a click on the backdrop; without it the dialog stays until a
 * button closes it.
 */
export function Dialog({
  title,
  titleClass,
  children,
  actions,
  onDismiss,
}: {
  title: string;
  titleClass?: string;
  children: ReactNode;
  actions?: ReactNode;
  onDismiss?: () => void;
}): ReactNode {
  const ref = useRef<HTMLDivElement>(null);
  const backdrop = useRef<HTMLDivElement>(null);

  // Modal behaviour: the page behind is inert, Tab cycles inside the dialog, and focus returns to
  // the control that opened it.
  useEffect(() => {
    const opener = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const inerted: Element[] = [];
    for (let node = backdrop.current; node?.parentElement; node = node.parentElement) {
      for (const sibling of Array.from(node.parentElement.children)) {
        if (sibling !== node && !sibling.hasAttribute('inert')) {
          sibling.setAttribute('inert', '');
          inerted.push(sibling);
        }
      }
    }
    ref.current?.focus();
    return () => {
      inerted.forEach((element) => element.removeAttribute('inert'));
      if (opener?.isConnected) opener.focus();
    };
  }, []);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onDismiss?.();
      if (event.key !== 'Tab' || !ref.current) return;
      const focusable = Array.from(
        ref.current.querySelectorAll<HTMLElement>(
          'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
        ),
      );
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (!first || !last) {
        event.preventDefault();
        ref.current.focus();
      } else if (
        event.shiftKey &&
        (document.activeElement === first || document.activeElement === ref.current)
      ) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      } else if (!ref.current.contains(document.activeElement)) {
        event.preventDefault();
        first.focus();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onDismiss]);

  return (
    <div ref={backdrop} className="backdrop" onClick={onDismiss}>
      <div
        ref={ref}
        className="dialog"
        role="dialog"
        aria-modal="true"
        aria-label={title}
        tabIndex={-1}
        onClick={(event) => event.stopPropagation()}
      >
        <h2 className={titleClass}>{title}</h2>
        <div className="dialog-body">{children}</div>
        {actions ? <div className="dialog-actions">{actions}</div> : null}
      </div>
    </div>
  );
}
