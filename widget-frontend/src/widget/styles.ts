export const widgetStyles = `
:host {
  all: initial;
  display: block;
  width: 100%;
  font-family:
    Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI",
    sans-serif;
}

.cloud-comment,
.cloud-comment[data-theme="light"] {
  --cc-surface: #ffffff;
  --cc-surface-muted: #f8fafc;
  --cc-surface-soft: #fbfdff;
  --cc-border: #d5dce8;
  --cc-border-soft: #e5eaf2;
  --cc-text: #475467;
  --cc-text-heading: #101828;
  --cc-placeholder: #8994a6;
  --cc-accent: #0f766e;
  --cc-accent-strong: #0d5f59;
  --cc-accent-contrast: #ffffff;
  --cc-accent-soft: #ecfdf3;
  --cc-accent-border: #b9f0da;
  --cc-success-bg: #f1fff7;
  --cc-success-text: #067647;
  --cc-warning-bg: #fff7ed;
  --cc-warning-text: #b54708;
  --cc-danger-bg: #fff5f4;
  --cc-danger-border: #fecdca;
  --cc-danger-text: #b42318;
  --cc-shadow: 0 18px 45px rgba(21, 31, 53, 0.08);
  --cc-shadow-sm: 0 10px 24px rgba(21, 31, 53, 0.055);
  color-scheme: light;
}

.cloud-comment[data-theme="dark"] {
  --cc-surface: #141f31;
  --cc-surface-muted: #1d2a3d;
  --cc-surface-soft: #101a2a;
  --cc-border: #33445c;
  --cc-border-soft: #2a3a50;
  --cc-text: #aab7c8;
  --cc-text-heading: #f5f8fc;
  --cc-placeholder: #7d8ca3;
  --cc-accent: #5eead4;
  --cc-accent-strong: #2dd4bf;
  --cc-accent-contrast: #06221f;
  --cc-accent-soft: rgba(94, 234, 212, 0.12);
  --cc-accent-border: rgba(94, 234, 212, 0.28);
  --cc-success-bg: rgba(134, 239, 172, 0.13);
  --cc-success-text: #86efac;
  --cc-warning-bg: rgba(251, 191, 36, 0.13);
  --cc-warning-text: #fbbf24;
  --cc-danger-bg: rgba(251, 113, 133, 0.13);
  --cc-danger-border: rgba(251, 113, 133, 0.34);
  --cc-danger-text: #fb7185;
  --cc-shadow: 0 18px 45px rgba(0, 0, 0, 0.34);
  --cc-shadow-sm: 0 12px 28px rgba(0, 0, 0, 0.28);
  color-scheme: dark;
}

.cloud-comment {
  box-sizing: border-box;
  width: 100%;
  overflow: hidden;
  border: 1px solid var(--cc-border);
  border-radius: 8px;
  background: var(--cc-surface);
  color: var(--cc-text-heading);
  font-family: inherit;
  line-height: 1.5;
  box-shadow: var(--cc-shadow);
  animation: cloud-comment-enter 220ms ease-out both;
}

.cloud-comment *,
.cloud-comment *::before,
.cloud-comment *::after {
  box-sizing: border-box;
}

.cloud-comment__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  padding: 20px 22px 18px;
  border-bottom: 1px solid var(--cc-border-soft);
  background: linear-gradient(180deg, var(--cc-surface) 0%, var(--cc-surface-muted) 100%);
}

.cloud-comment__eyebrow {
  margin: 0 0 6px;
  color: var(--cc-accent);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0;
  text-transform: uppercase;
}

.cloud-comment__title {
  margin: 0;
  color: var(--cc-text-heading);
  font-size: 22px;
  font-weight: 800;
  letter-spacing: 0;
}

.cloud-comment__badge {
  flex: 0 0 auto;
  border: 1px solid var(--cc-accent-border);
  border-radius: 999px;
  background: var(--cc-accent-soft);
  color: var(--cc-accent);
  padding: 6px 11px;
  font-size: 12px;
  font-weight: 800;
  box-shadow: 0 8px 18px rgba(6, 118, 71, 0.1);
}

.cloud-comment__body {
  display: grid;
  gap: 18px;
  padding: 20px 22px 22px;
}

.cloud-comment__list {
  display: grid;
  gap: 12px;
}

.cloud-comment__empty {
  display: grid;
  gap: 6px;
  margin: 0;
  border: 1px dashed var(--cc-border);
  border-radius: 8px;
  background: var(--cc-surface-muted);
  padding: 22px;
  color: var(--cc-text);
  font-size: 14px;
  text-align: center;
}

.cloud-comment__message {
  margin: 0;
  border-radius: 8px;
  padding: 11px 13px;
  font-size: 14px;
  animation: cloud-comment-slide 180ms ease-out both;
}

.cloud-comment__message--error {
  border: 1px solid var(--cc-danger-border);
  background: var(--cc-danger-bg);
  color: var(--cc-danger-text);
}

.cloud-comment__message--notice {
  border: 1px solid var(--cc-accent-border);
  background: var(--cc-success-bg);
  color: var(--cc-success-text);
}

.cloud-comment__message--muted {
  border: 1px solid var(--cc-border);
  background: var(--cc-surface-muted);
  color: var(--cc-text);
}

.cloud-comment__comment {
  display: grid;
  gap: 10px;
  border: 1px solid var(--cc-border-soft);
  border-radius: 8px;
  padding: 14px;
  background: var(--cc-surface);
  box-shadow: var(--cc-shadow-sm);
  animation: cloud-comment-slide 180ms ease-out both;
}

.cloud-comment__comment-header {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  color: var(--cc-text);
  font-size: 12px;
}

.cloud-comment__avatar {
  display: inline-flex;
  width: 28px;
  height: 28px;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: var(--cc-accent-soft);
  color: var(--cc-accent);
  font-size: 12px;
  font-weight: 800;
}

.cloud-comment__comment-header strong {
  min-width: 0;
  overflow-wrap: anywhere;
  color: var(--cc-text-heading);
  font-size: 14px;
}

.cloud-comment__status {
  border-radius: 999px;
  background: var(--cc-warning-bg);
  color: var(--cc-warning-text);
  padding: 2px 8px;
  font-weight: 800;
}

.cloud-comment__comment-content {
  margin: 0;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
  color: var(--cc-text-heading);
  font-size: 14px;
}

.cloud-comment__replies {
  display: grid;
  gap: 8px;
  margin-left: 18px;
  padding-left: 12px;
  border-left: 2px solid var(--cc-border);
}

.cloud-comment__form {
  display: grid;
  gap: 12px;
  border-top: 1px solid var(--cc-border-soft);
  padding-top: 18px;
}

.cloud-comment__textarea,
.cloud-comment__input {
  width: 100%;
  min-width: 0;
  border: 1px solid var(--cc-border);
  border-radius: 8px;
  background: var(--cc-surface-soft);
  color: var(--cc-text-heading);
  font: inherit;
  transition:
    border-color 160ms ease,
    box-shadow 160ms ease,
    background 160ms ease;
}

.cloud-comment__textarea {
  min-height: 116px;
  resize: vertical;
  padding: 12px 13px;
}

.cloud-comment__input {
  padding: 10px 12px;
  font-size: 14px;
}

.cloud-comment__textarea::placeholder,
.cloud-comment__input::placeholder {
  color: var(--cc-placeholder);
}

.cloud-comment__textarea:disabled {
  background: var(--cc-surface-muted);
  color: var(--cc-placeholder);
  cursor: not-allowed;
}

.cloud-comment__textarea:focus,
.cloud-comment__input:focus {
  border-color: var(--cc-accent);
  background: var(--cc-surface);
  outline: 0;
  box-shadow: 0 0 0 3px var(--cc-accent-soft);
}

.cloud-comment__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.cloud-comment__meta {
  min-width: 0;
  overflow-wrap: anywhere;
  color: var(--cc-text);
  font-size: 12px;
}

.cloud-comment__button {
  flex: 0 0 auto;
  border: 0;
  border-radius: 8px;
  background: var(--cc-accent);
  color: var(--cc-accent-contrast);
  cursor: pointer;
  font: inherit;
  font-size: 14px;
  font-weight: 800;
  padding: 10px 16px;
  transition:
    transform 150ms ease,
    box-shadow 150ms ease,
    background 150ms ease,
    opacity 150ms ease;
  box-shadow: 0 10px 22px rgba(15, 118, 110, 0.24);
}

.cloud-comment__button:hover {
  background: var(--cc-accent-strong);
  transform: translateY(-1px);
  box-shadow: 0 14px 28px rgba(15, 118, 110, 0.3);
}

.cloud-comment__button:active {
  transform: translateY(0);
}

.cloud-comment__button:disabled {
  background: var(--cc-placeholder);
  cursor: not-allowed;
  opacity: 0.72;
  transform: none;
  box-shadow: none;
}

.cloud-comment__button--secondary {
  background: var(--cc-text-heading);
  color: var(--cc-surface);
  box-shadow: var(--cc-shadow-sm);
}

.cloud-comment__button--secondary:hover {
  background: var(--cc-text-heading);
  box-shadow: var(--cc-shadow);
}

.cloud-comment__auth {
  display: grid;
  gap: 12px;
  border-top: 1px solid var(--cc-border-soft);
  padding-top: 18px;
}

.cloud-comment__tabs {
  display: inline-grid;
  width: min(100%, 320px);
  grid-template-columns: repeat(2, minmax(0, 1fr));
  overflow: hidden;
  border: 1px solid var(--cc-border);
  border-radius: 8px;
  background: var(--cc-surface-muted);
  padding: 3px;
}

.cloud-comment__tab {
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--cc-text);
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  font-weight: 800;
  padding: 8px 12px;
  transition:
    background 150ms ease,
    color 150ms ease,
    box-shadow 150ms ease;
}

.cloud-comment__tab--active {
  background: var(--cc-surface);
  color: var(--cc-accent);
  box-shadow: var(--cc-shadow-sm);
}

.cloud-comment__auth-form {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto;
  gap: 10px;
}

@keyframes cloud-comment-enter {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes cloud-comment-slide {
  from {
    opacity: 0;
    transform: translateY(4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .cloud-comment,
  .cloud-comment__comment,
  .cloud-comment__message {
    animation: none;
  }

  .cloud-comment__button {
    transition: none;
  }
}

@media (max-width: 640px) {
  .cloud-comment__header,
  .cloud-comment__actions {
    align-items: stretch;
    flex-direction: column;
  }

  .cloud-comment__body,
  .cloud-comment__header {
    padding-left: 16px;
    padding-right: 16px;
  }

  .cloud-comment__auth-form {
    grid-template-columns: 1fr;
  }

  .cloud-comment__button {
    width: 100%;
  }
}
`;
