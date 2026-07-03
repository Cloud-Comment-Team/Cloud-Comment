import { createWidgetApiClient, WidgetApiError } from "./api";
import { ADMIN_AUTH_TOKEN_KEY, WIDGET_AUTH_TOKEN_KEY } from "./config";
import { widgetStyles } from "./styles";
import type {
  CloudCommentWidgetInstance,
  CloudCommentWidgetOptions,
  ConsentRequirements,
  LoginResponse,
  PublicComment,
  PublicWidgetConfig,
  WidgetTheme
} from "./types";

type AuthMode = "login" | "register";
type ResolvedWidgetTheme = "light" | "dark";

type WidgetState = {
  loading: boolean;
  submitting: boolean;
  authenticating: boolean;
  comments: PublicComment[];
  config: PublicWidgetConfig | null;
  consentRequirements: ConsentRequirements | null;
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
  shell.setAttribute("aria-label", "Комментарии CloudComment");
  const themeController = createThemeController(shell, options.theme);

  shadowRoot.append(style, shell);

  const api = createWidgetApiClient(options.apiBaseUrl, options.siteId, options.pageUrl);
  const state: WidgetState = {
    loading: true,
    submitting: false,
    authenticating: false,
    comments: [],
    config: null,
    consentRequirements: null,
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
      const [config, comments, consentRequirements] = await Promise.all([
        api.getConfig(),
        api.listComments(),
        api.getConsentRequirements()
      ]);
      state.config = config;
      state.comments = comments.items;
      state.consentRequirements = consentRequirements;
    } catch (error) {
      state.error = getErrorMessage(error);
    } finally {
      state.loading = false;
      render();
    }
  }

  async function authenticate(email: string, password: string, consentAccepted: boolean): Promise<void> {
    state.authenticating = true;
    state.authError = null;
    render();

    try {
      let loginResponse: LoginResponse;
      if (state.authMode === "register") {
        if (!consentAccepted) {
          throw new WidgetApiError("Необходимо согласие на обработку персональных данных.");
        }
        const requirements = state.consentRequirements;
        if (!requirements) {
          throw new WidgetApiError("Требования согласия ещё загружаются. Попробуйте снова.");
        }
        await api.register({
          email,
          password,
          acceptedPrivacyPolicy: true,
          acceptedTerms: true,
          privacyPolicyVersion: requirements.privacyPolicyVersion,
          termsVersion: requirements.termsVersion
        });
        loginResponse = await api.login(email, password);
      } else {
        loginResponse = await api.login(email, password);
      }

      state.token = loginResponse.token;
      state.userEmail = loginResponse.user.email;
      storeAuthToken(loginResponse.token);
      state.notice = "Вы вошли и можете оставить комментарий.";
    } catch (error) {
      state.authError = getErrorMessage(error);
    } finally {
      state.authenticating = false;
      render();
    }
  }

  async function submitComment(content: string): Promise<void> {
    if (!state.token) {
      state.authError = "Войдите или зарегистрируйтесь, чтобы оставить комментарий.";
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
          ? "Комментарий опубликован."
          : "Комментарий отправлен и ждет модерации.";
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
      void authenticate(
        String(formData.get("email") ?? ""),
        String(formData.get("password") ?? ""),
        formData.get("consent") === "on"
      );
      return;
    }

    if (form.dataset.cloudCommentForm === "comment") {
      const formData = new FormData(form);
      const content = String(formData.get("comment") ?? "").trim();
      if (!content) {
        state.error = "Напишите комментарий перед отправкой.";
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
      themeController.destroy();
      shadowRoot.replaceChildren();
    }
  };
}

function createThemeController(shell: HTMLElement, theme: WidgetTheme): { destroy: () => void } {
  const applyTheme = () => {
    shell.dataset.theme = resolveWidgetTheme(theme);
  };

  applyTheme();

  if (theme !== "auto") {
    return { destroy: () => undefined };
  }

  const mediaQuery = window.matchMedia("(prefers-color-scheme: dark)");
  const observer = new MutationObserver(applyTheme);
  observer.observe(document.documentElement, { attributes: true, attributeFilter: ["class", "data-theme", "style"] });

  if (document.body) {
    observer.observe(document.body, { attributes: true, attributeFilter: ["class", "data-theme", "style"] });
  }

  mediaQuery.addEventListener("change", applyTheme);

  return {
    destroy: () => {
      observer.disconnect();
      mediaQuery.removeEventListener("change", applyTheme);
    }
  };
}

function resolveWidgetTheme(theme: WidgetTheme): ResolvedWidgetTheme {
  if (theme === "light" || theme === "dark") {
    return theme;
  }

  return detectHostTheme() ?? (window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light");
}

function detectHostTheme(): ResolvedWidgetTheme | null {
  for (const element of [document.documentElement, document.body].filter(Boolean) as HTMLElement[]) {
    const datasetTheme = normalizeThemeName(element.dataset.theme);
    if (datasetTheme) {
      return datasetTheme;
    }

    if (element.classList.contains("dark")) {
      return "dark";
    }
    if (element.classList.contains("light")) {
      return "light";
    }

    const colorScheme = window.getComputedStyle(element).colorScheme.toLowerCase().split(/\s+/)[0];
    const styleTheme = normalizeThemeName(colorScheme);
    if (styleTheme) {
      return styleTheme;
    }
  }

  return null;
}

function normalizeThemeName(value: string | undefined): ResolvedWidgetTheme | null {
  const normalized = value?.trim().toLowerCase();
  return normalized === "light" || normalized === "dark" ? normalized : null;
}

function renderHeader(
  options: Required<CloudCommentWidgetOptions>,
  state: WidgetState
): HTMLElement {
  const header = document.createElement("header");
  header.className = "cloud-comment__header";

  const titleBlock = document.createElement("div");

  const eyebrow = document.createElement("p");
  eyebrow.className = "cloud-comment__eyebrow";
  eyebrow.textContent = "Обсуждение";

  const title = document.createElement("h2");
  title.className = "cloud-comment__title";
  title.textContent = "Комментарии";

  titleBlock.append(eyebrow, title);

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
    body.append(renderMessage("Загружаем комментарии...", "muted"));
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
    empty.textContent = "Пока нет комментариев. Будьте первым, кто начнет обсуждение.";
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

  const avatar = document.createElement("span");
  avatar.className = "cloud-comment__avatar";
  avatar.textContent = getInitials(comment.author.displayName || comment.author.email);

  const author = document.createElement("strong");
  author.textContent = comment.author.displayName || comment.author.email;

  const date = document.createElement("time");
  date.dateTime = comment.createdAt;
  date.textContent = formatDate(comment.createdAt);

  header.append(avatar, author, date);

  if (comment.status !== "APPROVED") {
    const status = document.createElement("span");
    status.className = "cloud-comment__status";
    status.textContent = getStatusLabel(comment.status);
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
  textarea.placeholder = state.token ? "Напишите комментарий" : "Войдите, чтобы написать комментарий";
  textarea.setAttribute("aria-label", "Написать комментарий");
  textarea.maxLength = 5000;
  textarea.disabled = state.submitting || !state.token;

  const actions = document.createElement("div");
  actions.className = "cloud-comment__actions";

  const meta = document.createElement("span");
  meta.className = "cloud-comment__meta";
  meta.textContent = state.userEmail ? `Вы вошли как ${state.userEmail}` : state.token ? "Вы авторизованы" : "";

  const button = document.createElement("button");
  button.className = "cloud-comment__button";
  button.type = "submit";
  button.disabled = state.submitting || !state.token;
  button.textContent = state.submitting ? "Отправляем..." : "Отправить";

  actions.append(meta, button);
  form.append(textarea, actions);
  return form;
}

function renderAuthSection(state: WidgetState): HTMLElement {
  const section = document.createElement("section");
  section.className = "cloud-comment__auth";

  if (state.token) {
    section.hidden = true;
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
    button.textContent = mode === "login" ? "Войти" : "Регистрация";
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
  password.placeholder = "Пароль";
  password.required = true;
  password.minLength = 8;
  password.maxLength = 72;

  if (state.authMode === "register") {
    const consentLabel = document.createElement("label");
    consentLabel.className = "cloud-comment__consent";

    const consent = document.createElement("input");
    consent.type = "checkbox";
    consent.name = "consent";
    consent.required = true;

    const consentText = document.createElement("span");
    consentText.className = "cloud-comment__consent-text";
    consentText.textContent = "Согласен(на) на обработку персональных данных";

    if (state.consentRequirements) {
      const privacyLink = document.createElement("a");
      privacyLink.href = state.consentRequirements.privacyPolicyUrl;
      privacyLink.target = "_blank";
      privacyLink.rel = "noreferrer";
      privacyLink.textContent = "политика";

      const termsLink = document.createElement("a");
      termsLink.href = state.consentRequirements.termsUrl;
      termsLink.target = "_blank";
      termsLink.rel = "noreferrer";
      termsLink.textContent = "условия";

      consentText.replaceChildren(
        "Согласен(на) на обработку ПДн (",
        privacyLink,
        ", ",
        termsLink,
        ")"
      );
    }

    consentLabel.append(consent, consentText);
    form.append(consentLabel);
  }

  const submit = document.createElement("button");
  submit.className = "cloud-comment__button cloud-comment__button--secondary";
  submit.type = "submit";
  submit.disabled = state.authenticating;
  submit.textContent = state.authenticating
    ? "Подождите..."
    : state.authMode === "login"
      ? "Войти"
      : "Создать аккаунт";

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
  return "CloudComment не смог обработать запрос. Попробуйте еще раз.";
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

function getStatusLabel(status: PublicComment["status"]): string {
  if (status === "PENDING") {
    return "На модерации";
  }
  if (status === "APPROVED") {
    return "Опубликован";
  }
  return status;
}

function getInitials(value: string): string {
  const normalized = value.trim();
  if (!normalized) {
    return "CC";
  }
  return normalized.slice(0, 2).toUpperCase();
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
