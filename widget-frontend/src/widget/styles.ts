export const widgetStyles = `
:host {
  all: initial;
  color-scheme: light;
  font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
}

.cloud-comment {
  box-sizing: border-box;
  width: 100%;
  max-width: 760px;
  border: 1px solid #d8dee8;
  border-radius: 8px;
  background: #ffffff;
  color: #182230;
  font-family: inherit;
  line-height: 1.45;
}

.cloud-comment *,
.cloud-comment *::before,
.cloud-comment *::after {
  box-sizing: border-box;
}

.cloud-comment__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 18px;
  border-bottom: 1px solid #e7ebf2;
}

.cloud-comment__title {
  margin: 0;
  color: #101828;
  font-size: 18px;
  font-weight: 700;
}

.cloud-comment__badge {
  flex: 0 0 auto;
  border-radius: 999px;
  background: #ecfdf3;
  color: #067647;
  padding: 4px 10px;
  font-size: 12px;
  font-weight: 700;
}

.cloud-comment__body {
  display: grid;
  gap: 16px;
  padding: 18px;
}

.cloud-comment__empty {
  margin: 0;
  color: #475467;
  font-size: 14px;
}

.cloud-comment__message {
  margin: 0;
  border-radius: 6px;
  padding: 10px 12px;
  font-size: 14px;
}

.cloud-comment__message--error {
  border: 1px solid #fecdca;
  background: #fffbfa;
  color: #b42318;
}

.cloud-comment__message--notice {
  border: 1px solid #abefc6;
  background: #f6fef9;
  color: #067647;
}

.cloud-comment__message--muted {
  border: 1px solid #e7ebf2;
  background: #f8fafc;
  color: #475467;
}

.cloud-comment__list {
  display: grid;
  gap: 12px;
}

.cloud-comment__comment {
  display: grid;
  gap: 8px;
  border: 1px solid #e7ebf2;
  border-radius: 8px;
  padding: 12px;
  background: #ffffff;
}

.cloud-comment__comment-header {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  color: #475467;
  font-size: 12px;
}

.cloud-comment__comment-header strong {
  color: #182230;
  font-size: 14px;
}

.cloud-comment__status {
  border-radius: 999px;
  background: #fff7ed;
  color: #b54708;
  padding: 2px 8px;
  font-weight: 700;
}

.cloud-comment__comment-content {
  margin: 0;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
  color: #182230;
  font-size: 14px;
}

.cloud-comment__replies {
  display: grid;
  gap: 8px;
  margin-left: 16px;
  padding-left: 12px;
  border-left: 2px solid #e7ebf2;
}

.cloud-comment__form {
  display: grid;
  gap: 10px;
}

.cloud-comment__textarea {
  min-height: 96px;
  resize: vertical;
  border: 1px solid #cfd6e4;
  border-radius: 6px;
  padding: 10px 12px;
  color: #182230;
  font: inherit;
}

.cloud-comment__textarea:disabled {
  background: #f8fafc;
  color: #98a2b3;
  cursor: not-allowed;
}

.cloud-comment__textarea:focus {
  border-color: #3478f6;
  outline: 2px solid rgba(52, 120, 246, 0.18);
  outline-offset: 1px;
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
  color: #667085;
  font-size: 12px;
}

.cloud-comment__button {
  flex: 0 0 auto;
  border: 0;
  border-radius: 6px;
  background: #175cd3;
  color: #ffffff;
  cursor: pointer;
  font: inherit;
  font-size: 14px;
  font-weight: 700;
  padding: 9px 14px;
}

.cloud-comment__button:hover {
  background: #1849a9;
}

.cloud-comment__button:disabled {
  background: #98a2b3;
  cursor: not-allowed;
}

.cloud-comment__button--secondary {
  background: #344054;
}

.cloud-comment__button--secondary:hover {
  background: #182230;
}

.cloud-comment__auth {
  display: grid;
  gap: 10px;
  border-top: 1px solid #e7ebf2;
  padding-top: 16px;
}

.cloud-comment__tabs {
  display: inline-flex;
  width: fit-content;
  overflow: hidden;
  border: 1px solid #cfd6e4;
  border-radius: 6px;
}

.cloud-comment__tab {
  border: 0;
  background: #ffffff;
  color: #475467;
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  font-weight: 700;
  padding: 8px 12px;
}

.cloud-comment__tab + .cloud-comment__tab {
  border-left: 1px solid #cfd6e4;
}

.cloud-comment__tab--active {
  background: #eef4ff;
  color: #175cd3;
}

.cloud-comment__auth-form {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto;
  gap: 10px;
}

.cloud-comment__input {
  min-width: 0;
  border: 1px solid #cfd6e4;
  border-radius: 6px;
  padding: 9px 10px;
  color: #182230;
  font: inherit;
  font-size: 14px;
}

.cloud-comment__input:focus {
  border-color: #3478f6;
  outline: 2px solid rgba(52, 120, 246, 0.18);
  outline-offset: 1px;
}

@media (max-width: 560px) {
  .cloud-comment__header,
  .cloud-comment__actions {
    align-items: flex-start;
    flex-direction: column;
  }

  .cloud-comment__auth-form {
    grid-template-columns: 1fr;
  }

  .cloud-comment__button {
    width: 100%;
  }
}
`;
