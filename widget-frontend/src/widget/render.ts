import { createWidgetApiClient, WidgetApiError } from "./api";
import { ADMIN_AUTH_TOKEN_KEY, WIDGET_AUTH_TOKEN_KEY } from "./config";
import { widgetStyles } from "./styles";
import type {
  CloudCommentWidgetInstance,
  CloudCommentWidgetOptions,
  LoginResponse,
  PublicComment,
  PublicWidgetConfig
} from "./types";

type AuthMode = "login" | "register";

type WidgetState = {
  loading: boolean;
  submitting: boolean;
  authenticating: boolean;
  comments: PublicComment[];
  config: PublicWidgetConfig | null;
  token: string | null;
  userEmail: string | null;
  error: string | null;
  authError: string | null;
  notice: string | null;
  authMode: AuthMode;
};

export function renderWidget(
  root: HTMLElement,
  options: Required<CloudCommentWidgetOptions>
): CloudCommentWidgetInstance {
  const shadowRoot = root.shadowRoot ?? root.attachShadow({ mode: "open" });
  shadowRoot.replaceChildren();

  const style = document.createElement("style");
  style.textContent = widgetStyles;

  const shell = document.createElement("section");
  shell.className = "cloud-comment";
  shell.setAttribute("aria-label", "CloudComment comments");

  shadowRoot.append(style, shell);

  const api = createWidgetApiClient(options.apiBaseUrl, options.siteId, options.pageUrl);
  const state: WidgetState = {
    loading: true,
    submitting: false,
    authenticating: false,
    comments: [],
    config: null,
    token: getStoredAuthToken(),
    userEmail: null,
    error: null,
    authError: null,
    notice: null,
    authMode: "login"
  };
  let destroyed = false;

  function render(): void {
    if (destroyed) {
      return;
    }

    shell.replaceChildren(renderHeader(options, state), renderBody(state));
  }

  async function loadInitialData(): Promise<void> {
    state.loading = true;
    state.error = null;
    render();

    try {
      const [config, comments] = await Promise.all([api.getConfig(), api.listComments()]);
      state.config = config;
      state.comments = comments.items;
    } catch (error) {
      state.error = getErrorMessage(error);
    } finally {
      state.loading = false;
      render();
    }
  }

  async function authenticate(email: string, password: string): Promise<void> {
    state.authenticating = true;
    state.authError = null;
    render();

    try {
      let loginResponse: LoginResponse;
      if (state.authMode === "register") {
        await api.register(email, password);
        loginResponse = await api.login(email, password);
      } else {
        loginResponse = await api.login(email, password);
      }

      state.token = loginResponse.token;
      state.userEmail = loginResponse.user.email;
      storeAuthToken(loginResponse.token);
      state.notice = "You are signed in.";
    } catch (error) {
      state.authError = getErrorMessage(error);
    } finally {
      state.authenticating = false;
      render();
    }
  }

  async function submitComment(content: string): Promise<void> {
    if (!state.token) {
      state.authError = "Please sign in before posting a comment.";
      render();
      return;
    }

    state.submitting = true;
    state.error = null;
    state.notice = null;
    render();

    try {
      const createdComment = await api.createComment(content, state.token);
      const refreshedComments = await api.listComments();
      state.comments = mergeCreatedComment(createdComment, refreshedComments.items);
      state.notice =
        createdComment.status === "APPROVED"
          ? "Your comment was posted."
          : "Your comment was submitted and is waiting for moderation.";
    } catch (error) {
      if (error instanceof WidgetApiError && error.status === 401) {
        state.token = null;
        removeStoredAuthToken();
      }
      state.error = getErrorMessage(error);
    } finally {
      state.submitting = false;
      render();
    }
  }

  shell.addEventListener("submit", (event) => {
    const form = event.target;
    if (!(form instanceof HTMLFormElement)) {
      return;
    }
    event.preventDefault();

    if (form.dataset.cloudCommentForm === "auth") {
      const formData = new FormData(form);
      void authenticate(String(formData.get("email") ?? ""), String(formData.get("password") ?? ""));
      return;
    }

    if (form.dataset.cloudCommentForm === "comment") {
      const formData = new FormData(form);
      const content = String(formData.get("comment") ?? "").trim();
      if (!content) {
        state.error = "Write a comment before sending.";
        render();
        return;
      }
      form.reset();
      void submitComment(content);
    }
  });

  shell.addEventListener("click", (event) => {
    const button = event.target;
    if (!(button instanceof HTMLButtonElement)) {
      return;
    }

    if (button.dataset.authMode === "login" || button.dataset.authMode === "register") {
      state.authMode = button.dataset.authMode;
      state.authError = null;
      render();
    }
  });

  render();
  void loadInitialData();

  return {
    destroy: () => {
      destroyed = true;
      shadowRoot.replaceChildren();
    }
  };
}

function renderHeader(
  options: Required<CloudCommentWidgetOptions>,
  state: WidgetState
): HTMLElement {
  const header = document.createElement("header");
  header.className = "cloud-comment__header";

  const titleBlock = document.createElement("div");

  const title = document.createElement("h2");
  title.className = "cloud-comment__title";
  title.textContent = "Comments";

  const meta = document.createElement("p");
  meta.className = "cloud-comment__meta";
  meta.textContent = state.config
    ? `${state.config.moderationMode === "PRE_MODERATION" ? "Pre-moderated" : "Live"} discussion`
    : getPageLabel(options.pageUrl);

  titleBlock.append(title, meta);

  const badge = document.createElement("span");
  badge.className = "cloud-comment__badge";
  badge.textContent = "CloudComment";

  header.append(titleBlock, badge);
  return header;
}

function renderBody(state: WidgetState): HTMLElement {
  const body = document.createElement("div");
  body.className = "cloud-comment__body";

  if (state.error) {
    body.append(renderMessage(state.error, "error"));
  }

  if (state.notice) {
    body.append(renderMessage(state.notice, "notice"));
  }

  if (state.loading) {
    body.append(renderMessage("Loading comments...", "muted"));
  } else {
    body.append(renderCommentList(state.comments));
  }

  body.append(renderCommentForm(state), renderAuthSection(state));
  return body;
}

function renderCommentList(comments: PublicComment[]): HTMLElement {
  const list = document.createElement("div");
  list.className = "cloud-comment__list";

  if (comments.length === 0) {
    const empty = document.createElement("p");
    empty.className = "cloud-comment__empty";
    empty.textContent = "No comments yet.";
    list.append(empty);
    return list;
  }

  for (const comment of comments) {
    list.append(renderComment(comment));
  }

  return list;
}

function renderComment(comment: PublicComment): HTMLElement {
  const article = document.createElement("article");
  article.className = "cloud-comment__comment";

  const header = document.createElement("header");
  header.className = "cloud-comment__comment-header";

  const author = document.createElement("strong");
  author.textContent = comment.author.displayName || comment.author.email;

  const date = document.createElement("time");
  date.dateTime = comment.createdAt;
  date.textContent = formatDate(comment.createdAt);

  header.append(author, date);

  if (comment.status !== "APPROVED") {
    const status = document.createElement("span");
    status.className = "cloud-comment__status";
    status.textContent = comment.status === "PENDING" ? "Pending" : comment.status;
    header.append(status);
  }

  const content = document.createElement("p");
  content.className = "cloud-comment__comment-content";
  content.textContent = comment.content;

  article.append(header, content);

  if (comment.replies.length > 0) {
    const replies = document.createElement("div");
    replies.className = "cloud-comment__replies";
    for (const reply of comment.replies) {
      replies.append(renderComment(reply));
    }
    article.append(replies);
  }

  return article;
}

function renderCommentForm(state: WidgetState): HTMLElement {
  const form = document.createElement("form");
  form.className = "cloud-comment__form";
  form.dataset.cloudCommentForm = "comment";

  const textarea = document.createElement("textarea");
  textarea.className = "cloud-comment__textarea";
  textarea.name = "comment";
  textarea.placeholder = state.token ? "Write a comment" : "Sign in to write a comment";
  textarea.setAttribute("aria-label", "Write a comment");
  textarea.maxLength = 5000;
  textarea.disabled = state.submitting || !state.token;

  const actions = document.createElement("div");
  actions.className = "cloud-comment__actions";

  const meta = document.createElement("span");
  meta.className = "cloud-comment__meta";
  meta.textContent = state.userEmail ? `Signed in as ${state.userEmail}` : "";

  const button = document.createElement("button");
  button.className = "cloud-comment__button";
  button.type = "submit";
  button.disabled = state.submitting || !state.token;
  button.textContent = state.submitting ? "Sending..." : "Send";

  actions.append(meta, button);
  form.append(textarea, actions);
  return form;
}

function renderAuthSection(state: WidgetState): HTMLElement {
  const section = document.createElement("section");
  section.className = "cloud-comment__auth";

  if (state.token) {
    return section;
  }

  const tabs = document.createElement("div");
  tabs.className = "cloud-comment__tabs";

  for (const mode of ["login", "register"] as const) {
    const button = document.createElement("button");
    button.type = "button";
    button.className =
      mode === state.authMode ? "cloud-comment__tab cloud-comment__tab--active" : "cloud-comment__tab";
    button.dataset.authMode = mode;
    button.textContent = mode === "login" ? "Login" : "Register";
    tabs.append(button);
  }

  const form = document.createElement("form");
  form.className = "cloud-comment__auth-form";
  form.dataset.cloudCommentForm = "auth";

  const email = document.createElement("input");
  email.className = "cloud-comment__input";
  email.name = "email";
  email.type = "email";
  email.autocomplete = "email";
  email.placeholder = "Email";
  email.required = true;
  email.maxLength = 320;

  const password = document.createElement("input");
  password.className = "cloud-comment__input";
  password.name = "password";
  password.type = "password";
  password.autocomplete = state.authMode === "login" ? "current-password" : "new-password";
  password.placeholder = "Password";
  password.required = true;
  password.minLength = 8;
  password.maxLength = 72;

  const submit = document.createElement("button");
  submit.className = "cloud-comment__button cloud-comment__button--secondary";
  submit.type = "submit";
  submit.disabled = state.authenticating;
  submit.textContent = state.authenticating
    ? "Please wait..."
    : state.authMode === "login"
      ? "Login"
      : "Register";

  form.append(email, password, submit);
  section.append(tabs);

  if (state.authError) {
    section.append(renderMessage(state.authError, "error"));
  }

  section.append(form);
  return section;
}

function renderMessage(message: string, tone: "error" | "notice" | "muted"): HTMLElement {
  const element = document.createElement("p");
  element.className = `cloud-comment__message cloud-comment__message--${tone}`;
  element.textContent = message;
  return element;
}

function mergeCreatedComment(createdComment: PublicComment, comments: PublicComment[]): PublicComment[] {
  if (comments.some((comment) => comment.id === createdComment.id)) {
    return comments;
  }

  return [createdComment, ...comments];
}

function getErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }
  return "CloudComment could not process the request. Please try again.";
}

function formatDate(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "";
  }
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(date);
}

function getPageLabel(pageUrl: string): string {
  try {
    return new URL(pageUrl).hostname;
  } catch {
    return pageUrl;
  }
}

function getStoredAuthToken(): string | null {
  return (
    readStorage(window.localStorage, WIDGET_AUTH_TOKEN_KEY) ??
    readStorage(window.sessionStorage, WIDGET_AUTH_TOKEN_KEY) ??
    readStorage(window.localStorage, ADMIN_AUTH_TOKEN_KEY) ??
    readStorage(window.sessionStorage, ADMIN_AUTH_TOKEN_KEY)
  );
}

function storeAuthToken(token: string): void {
  try {
    window.localStorage.setItem(WIDGET_AUTH_TOKEN_KEY, token);
  } catch {
    // Storage may be blocked on embedded pages; the in-memory token still works.
  }
}

function removeStoredAuthToken(): void {
  try {
    window.localStorage.removeItem(WIDGET_AUTH_TOKEN_KEY);
  } catch {
    // Ignore storage cleanup failures on embedded pages.
  }
}

function readStorage(storage: Storage, key: string): string | null {
  try {
    return storage.getItem(key);
  } catch {
    return null;
  }
}
