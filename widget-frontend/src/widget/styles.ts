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
  padding: 18px;
}

.cloud-comment__empty {
  margin: 0 0 16px;
  color: #475467;
  font-size: 14px;
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
`;
