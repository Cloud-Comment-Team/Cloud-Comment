import { widgetStyles } from "./styles";
import type { CloudCommentWidgetOptions } from "./types";

export function renderWidget(root: HTMLElement, options: Required<CloudCommentWidgetOptions>): void {
  const shadowRoot = root.shadowRoot ?? root.attachShadow({ mode: "open" });
  shadowRoot.replaceChildren();

  const style = document.createElement("style");
  style.textContent = widgetStyles;

  const shell = document.createElement("section");
  shell.className = "cloud-comment";
  shell.setAttribute("aria-label", "CloudComment comments");
  shell.innerHTML = `
    <header class="cloud-comment__header">
      <h2 class="cloud-comment__title">Comments</h2>
      <span class="cloud-comment__badge">CloudComment</span>
    </header>
    <div class="cloud-comment__body">
      <p class="cloud-comment__empty">Comment loading will be connected to the public API in the next step.</p>
      <form class="cloud-comment__form">
        <textarea class="cloud-comment__textarea" name="comment" placeholder="Write a comment" aria-label="Write a comment"></textarea>
        <div class="cloud-comment__actions">
          <span class="cloud-comment__meta"></span>
          <button class="cloud-comment__button" type="submit">Send</button>
        </div>
      </form>
    </div>
  `;

  const meta = shell.querySelector<HTMLElement>(".cloud-comment__meta");
  if (meta) {
    meta.textContent = `site: ${options.siteId} | page: ${options.pageUrl}`;
  }

  const form = shell.querySelector<HTMLFormElement>(".cloud-comment__form");
  form?.addEventListener("submit", (event) => {
    event.preventDefault();
  });

  shadowRoot.append(style, shell);
}
