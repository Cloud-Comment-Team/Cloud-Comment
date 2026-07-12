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
  --cc-radius: 8px;
  --cc-accent: var(--cc-custom-accent, #0f766e);
  --cc-accent-strong: color-mix(in srgb, var(--cc-accent) 84%, #000000);
  --cc-accent-contrast: var(--cc-custom-accent-contrast, #ffffff);
  --cc-accent-soft: color-mix(in srgb, var(--cc-accent) 12%, transparent);
  --cc-accent-border: color-mix(in srgb, var(--cc-accent) 34%, transparent);
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
  --cc-accent: var(--cc-custom-accent, #5eead4);
  --cc-accent-strong: color-mix(in srgb, var(--cc-accent) 88%, #ffffff);
  --cc-accent-contrast: var(--cc-custom-accent-contrast, #06221f);
  --cc-accent-soft: color-mix(in srgb, var(--cc-accent) 14%, transparent);
  --cc-accent-border: color-mix(in srgb, var(--cc-accent) 30%, transparent);
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
  container-name: cloud-comment;
  container-type: inline-size;
  width: 100%;
  overflow: hidden;
  border: 1px solid var(--cc-border);
  border-radius: var(--cc-radius);
  background: var(--cc-surface);
  color: var(--cc-text-heading);
  font-family: inherit;
  line-height: 1.5;
  box-shadow: var(--cc-shadow);
  animation: cloud-comment-enter 220ms ease-out both;
}

.cloud-comment[data-content-width="readable"] {
  max-width: 720px;
}

.cloud-comment[data-content-width="wide"] {
  max-width: 960px;
}

.cloud-comment[data-alignment="center"] {
  margin-inline: auto;
}

.cloud-comment[data-alignment="left"] {
  margin-inline: 0 auto;
}

.cloud-comment[data-font-scale="small"] { font-size: 0.9rem; }
.cloud-comment[data-font-scale="medium"] { font-size: 1rem; }
.cloud-comment[data-font-scale="large"] { font-size: 1.1rem; }
.cloud-comment[data-font-family="system"] { font-family: ui-sans-serif, system-ui, sans-serif; }
.cloud-comment[data-font-family="serif"] { font-family: ui-serif, Georgia, serif; }
.cloud-comment[data-font-family="mono"] { font-family: ui-monospace, Consolas, monospace; }

.cloud-comment[data-elevation="border"] { box-shadow: none; }
.cloud-comment[data-elevation="shadow"] { box-shadow: var(--cc-shadow); }
.cloud-comment[data-elevation="none"] { border-color: transparent; box-shadow: none; }

.cloud-comment[data-density="compact"] .cloud-comment__header {
  padding: 14px 16px 12px;
}

.cloud-comment[data-density="compact"] .cloud-comment__body {
  gap: 12px;
  padding: 14px 16px 16px;
}

.cloud-comment[data-density="compact"] .cloud-comment__list {
  gap: 8px;
}

.cloud-comment[data-radius="small"] {
  --cc-radius: 6px;
}

.cloud-comment[data-radius="medium"] {
  --cc-radius: 10px;
}

.cloud-comment[data-radius="large"] {
  --cc-radius: 18px;
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

.cloud-comment__sort {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  color: var(--cc-text);
  font-size: 13px;
  font-weight: 700;
}

.cloud-comment__sort select {
  min-width: 190px;
  border: 1px solid var(--cc-border);
  border-radius: calc(var(--cc-radius) * 0.75);
  background: var(--cc-surface);
  color: var(--cc-text-heading);
  padding: 7px 30px 7px 10px;
  font: inherit;
}

.cloud-comment__sort select:focus-visible {
  outline: 2px solid var(--cc-accent);
  outline-offset: 2px;
}

.cloud-comment__empty {
  display: grid;
  gap: 6px;
  margin: 0;
  border: 1px dashed var(--cc-border);
  border-radius: var(--cc-radius);
  background: var(--cc-surface-muted);
  padding: 22px;
  color: var(--cc-text);
  font-size: 14px;
  text-align: center;
}

.cloud-comment__message {
  margin: 0;
  border-radius: var(--cc-radius);
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
  border-radius: var(--cc-radius);
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

.cloud-comment__pinned {
  margin-left: auto;
  border: 1px solid var(--cc-accent-border);
  border-radius: 999px;
  background: var(--cc-accent-soft);
  color: var(--cc-accent);
  padding: 3px 8px;
  font-size: 11px;
  font-weight: 800;
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

.cloud-comment__comment-footer {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-start;
  gap: 8px;
}

.cloud-comment__reactions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.cloud-comment__reaction {
  display: inline-flex;
  min-width: 44px;
  align-items: center;
  justify-content: center;
  gap: 5px;
  border: 1px solid var(--cc-border);
  border-radius: 999px;
  background: var(--cc-surface-muted);
  color: var(--cc-text);
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  font-weight: 800;
  line-height: 1;
  padding: 6px 9px;
  transition:
    border-color 150ms ease,
    background 150ms ease,
    color 150ms ease,
    transform 150ms ease;
}

.cloud-comment__reaction:hover {
  border-color: var(--cc-accent-border);
  background: var(--cc-accent-soft);
  color: var(--cc-accent);
  transform: translateY(-1px);
}

.cloud-comment__reaction--active {
  border-color: var(--cc-accent-border);
  background: var(--cc-accent);
  color: var(--cc-accent-contrast);
}

.cloud-comment__reaction:disabled {
  cursor: wait;
  opacity: 0.72;
  transform: none;
}

.cloud-comment__reaction-emoji {
  font-size: 14px;
}

.cloud-comment__reaction-count {
  font-variant-numeric: tabular-nums;
}

.cloud-comment__reply-button,
.cloud-comment__reply-cancel,
.cloud-comment__comment-action {
  border: 1px solid var(--cc-border);
  border-radius: 999px;
  background: var(--cc-surface-muted);
  color: var(--cc-accent);
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  font-weight: 800;
  padding: 5px 10px;
  transition:
    border-color 150ms ease,
    background 150ms ease,
    color 150ms ease,
    transform 150ms ease;
}

.cloud-comment__reply-button:hover,
.cloud-comment__reply-cancel:hover,
.cloud-comment__comment-action:hover {
  border-color: var(--cc-accent-border);
  background: var(--cc-accent-soft);
  transform: translateY(-1px);
}

.cloud-comment__reply-button:disabled,
.cloud-comment__reply-cancel:disabled,
.cloud-comment__comment-action:disabled {
  cursor: not-allowed;
  opacity: 0.62;
  transform: none;
}

.cloud-comment__comment-action--danger {
  color: var(--cc-danger-text);
}

.cloud-comment__edit-form {
  display: grid;
  gap: 10px;
}

.cloud-comment__inline-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.cloud-comment__delete-comment-confirm {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  border: 1px solid var(--cc-danger-border);
  border-radius: var(--cc-radius);
  background: var(--cc-danger-bg);
  color: var(--cc-danger-text);
  padding: 10px 12px;
  font-size: 13px;
  font-weight: 700;
}

.cloud-comment__replies {
  display: grid;
  gap: 8px;
  margin-left: 18px;
  padding-left: 12px;
  border-left: 2px solid var(--cc-border);
}

.cloud-comment__replies .cloud-comment__comment {
  background: var(--cc-surface-muted);
}

.cloud-comment__load-replies,
.cloud-comment__auth-expand {
  justify-self: start;
  border: 1px solid var(--cc-border);
  border-radius: 999px;
  background: var(--cc-surface-muted);
  color: var(--cc-accent);
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  font-weight: 800;
  padding: 8px 12px;
}

.cloud-comment__load-replies:focus-visible,
.cloud-comment__auth-expand:focus-visible {
  outline: 2px solid var(--cc-accent);
  outline-offset: 2px;
}

.cloud-comment__form {
  display: grid;
  gap: 12px;
  border-top: 1px solid var(--cc-border-soft);
  padding-top: 18px;
}

.cloud-comment__reply-context {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  border: 1px solid var(--cc-accent-border);
  border-radius: var(--cc-radius);
  background: var(--cc-accent-soft);
  color: var(--cc-accent);
  padding: 9px 11px;
  font-size: 13px;
  font-weight: 800;
}

.cloud-comment__reply-context span {
  min-width: 0;
  overflow-wrap: anywhere;
}

.cloud-comment__reply-cancel {
  flex: 0 0 auto;
  background: var(--cc-surface);
}

.cloud-comment__textarea,
.cloud-comment__input {
  width: 100%;
  min-width: 0;
  border: 1px solid var(--cc-border);
  border-radius: var(--cc-radius);
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

.cloud-comment__textarea[readonly] {
  background: var(--cc-surface-muted);
  cursor: pointer;
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
  border-radius: var(--cc-radius);
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

.cloud-comment__account {
  display: grid;
  gap: 12px;
  border: 1px solid var(--cc-border-soft);
  border-radius: var(--cc-radius);
  background: var(--cc-surface-muted);
  padding: 14px;
}

.cloud-comment__account[hidden] {
  display: none;
}

.cloud-comment__account-summary {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
  width: 100%;
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
  padding: 0;
  text-align: left;
}

.cloud-comment__account-summary:focus-visible {
  border-radius: var(--cc-radius);
  outline: 2px solid var(--cc-accent);
  outline-offset: 3px;
}

.cloud-comment__avatar--account {
  width: 34px;
  height: 34px;
}

.cloud-comment__account-text {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.cloud-comment__account-text strong {
  overflow-wrap: anywhere;
  color: var(--cc-text-heading);
  font-size: 14px;
}

.cloud-comment__account-text span {
  color: var(--cc-text);
  font-size: 12px;
}

.cloud-comment__account-actions,
.cloud-comment__delete-actions,
.cloud-comment__account-links {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.cloud-comment__account-button {
  border: 1px solid var(--cc-border);
  border-radius: var(--cc-radius);
  background: var(--cc-surface);
  color: var(--cc-text-heading);
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  font-weight: 800;
  padding: 8px 10px;
  transition:
    border-color 150ms ease,
    background 150ms ease,
    color 150ms ease,
    transform 150ms ease;
}

.cloud-comment__account-button:hover {
  border-color: var(--cc-accent-border);
  background: var(--cc-accent-soft);
  color: var(--cc-accent);
  transform: translateY(-1px);
}

.cloud-comment__account-button:disabled {
  cursor: not-allowed;
  opacity: 0.62;
  transform: none;
}

.cloud-comment__account-button--danger {
  border-color: var(--cc-danger-border);
  color: var(--cc-danger-text);
}

.cloud-comment__account-button--danger:hover {
  border-color: var(--cc-danger-border);
  background: var(--cc-danger-bg);
  color: var(--cc-danger-text);
}

.cloud-comment__account-links a {
  color: var(--cc-accent);
  font-size: 12px;
  font-weight: 700;
  text-decoration: none;
}

.cloud-comment__account-links a:hover {
  text-decoration: underline;
}

.cloud-comment__delete-confirm {
  display: grid;
  gap: 10px;
  border: 1px solid var(--cc-danger-border);
  border-radius: var(--cc-radius);
  background: var(--cc-danger-bg);
  padding: 12px;
}

.cloud-comment__delete-confirm p {
  margin: 0;
  color: var(--cc-danger-text);
  font-size: 13px;
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
  border-radius: var(--cc-radius);
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

.cloud-comment__consent {
  grid-column: 1 / -1;
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--cc-text);
}

.cloud-comment__consent input {
  margin-top: 2px;
  flex-shrink: 0;
}

.cloud-comment__consent-text a {
  color: var(--cc-accent-strong);
  text-decoration: underline;
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

@container cloud-comment (max-width: 640px) {
  .cloud-comment__header,
  .cloud-comment__body {
    padding-inline: 14px;
  }

  .cloud-comment__auth-form {
    grid-template-columns: 1fr;
  }

  .cloud-comment__replies {
    margin-left: 6px;
    padding-left: 8px;
  }
}

@container cloud-comment (max-width: 420px) {
  .cloud-comment__header {
    align-items: center;
  }

  .cloud-comment__badge {
    display: none;
  }

  .cloud-comment__sort {
    align-items: stretch;
    flex-direction: column;
  }

  .cloud-comment__sort select {
    width: 100%;
    min-width: 0;
  }

  .cloud-comment__actions {
    align-items: stretch;
    flex-direction: column;
  }

  .cloud-comment__button {
    width: 100%;
  }

  .cloud-comment__comment {
    padding: 11px;
  }
}
`;
