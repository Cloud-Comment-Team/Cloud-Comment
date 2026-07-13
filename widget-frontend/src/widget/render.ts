import { createWidgetApiClient, WidgetApiError } from "./api";
import { ADMIN_AUTH_TOKEN_KEY, WIDGET_AUTH_TOKEN_KEY } from "./config";
import { widgetStyles } from "./styles";
import type {
  CloudCommentWidgetInstance,
  CloudCommentWidgetOptions,
  CommentAuthor,
  CommentReaction,
  CommentReactionType,
  ConsentRequirements,
  LoginResponse,
  PublicComment,
  PublicCommentSort,
  PublicWidgetConfig,
  WidgetStyle,
  WidgetStyleTheme,
  WidgetTheme
} from "./types";

type AuthMode = "login" | "register";
type ResolvedWidgetTheme = "light" | "dark";
type ReplyTarget = {
  id: string;
  authorLabel: string;
};

type EditingComment = {
  id: string;
  content: string;
};

type WidgetState = {
  loading: boolean;
  submitting: boolean;
  authenticating: boolean;
  accountBusy: boolean;
  reactingCommentId: string | null;
  editingComment: EditingComment | null;
  updatingCommentId: string | null;
  deletingCommentId: string | null;
  confirmingCommentDeleteId: string | null;
  comments: PublicComment[];
  sort: PublicCommentSort;
  config: PublicWidgetConfig | null;
  consentRequirements: ConsentRequirements | null;
  token: string | null;
  userEmail: string | null;
  error: string | null;
  authError: string | null;
  accountError: string | null;
  notice: string | null;
  authMode: AuthMode;
  deleteConfirming: boolean;
  replyingTo: ReplyTarget | null;
  authExpanded: boolean;
  profileOpen: boolean;
  loadingRepliesId: string | null;
  replyPages: Record<string, number>;
  collapsedReplyIds: Set<string>;
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
    accountBusy: false,
    reactingCommentId: null,
    editingComment: null,
    updatingCommentId: null,
    deletingCommentId: null,
    confirmingCommentDeleteId: null,
    comments: [],
    sort: "PINNED_FIRST",
    config: null,
    consentRequirements: null,
    token: getStoredAuthToken(),
    userEmail: null,
    error: null,
    authError: null,
    accountError: null,
    notice: null,
    authMode: "login",
    deleteConfirming: false,
    replyingTo: null,
    authExpanded: false,
    profileOpen: false,
    loadingRepliesId: null,
    replyPages: {},
    collapsedReplyIds: new Set()
  };
  let destroyed = false;

  function render(): void {
    if (destroyed) {
      return;
    }

    const content = [renderHeader(options, state), renderBody(state, options)].filter(
      (element): element is HTMLElement => element !== null
    );
    shell.replaceChildren(...content);
  }

  async function loadInitialData(): Promise<void> {
    state.loading = true;
    state.error = null;
    render();

    try {
      const config = await api.getConfig();
      config.style = normalizeWidgetStyle(config.style);
      state.config = config;
      state.sort = config.style.defaultSort;
      applyWidgetStyle(shell, config.style);
      themeController.setConfiguredTheme(config.style.theme);
      const [comments, consentRequirements] = await Promise.all([
        api.listComments(state.sort, state.token),
        api.getConsentRequirements()
      ]);
      state.comments = comments.items;
      state.consentRequirements = consentRequirements;
      if (state.token) {
        await loadCurrentUser();
      }
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
      state.authExpanded = false;
      storeAuthToken(loginResponse.token);
      state.comments = (await api.listComments(state.sort, loginResponse.token)).items;
      state.notice = "Вы вошли и можете оставить комментарий.";
      state.accountError = null;
      state.deleteConfirming = false;
    } catch (error) {
      state.authError = getErrorMessage(error);
    } finally {
      state.authenticating = false;
      render();
    }
  }

  async function loadCurrentUser(): Promise<void> {
    if (!state.token) {
      return;
    }

    try {
      const user = await api.getCurrentUser(state.token);
      state.userEmail = user.email;
    } catch (error) {
      if (error instanceof WidgetApiError && error.status === 401) {
        clearAuthState();
        return;
      }
      state.accountError = getErrorMessage(error);
    }
  }

  async function logout(): Promise<void> {
    if (!state.token) {
      clearAuthState();
      render();
      return;
    }

    state.accountBusy = true;
    state.accountError = null;
    render();

    try {
      await api.logout(state.token);
      state.notice = "Вы вышли из аккаунта комментатора.";
    } catch {
      state.notice = "Вы вышли из виджета на этом устройстве.";
    } finally {
      clearAuthState();
      state.accountBusy = false;
      render();
    }
  }

  async function requestAccountDeletion(): Promise<void> {
    if (!state.token) {
      state.authError = "Войдите, чтобы запросить удаление аккаунта.";
      render();
      return;
    }

    state.accountBusy = true;
    state.accountError = null;
    render();

    try {
      const request = await api.requestAccountDeletion(state.token);
      state.deleteConfirming = false;
      state.notice = `Письмо для подтверждения удаления отправлено на ${state.userEmail ?? "ваш email"}. Код действует до ${formatDate(request.expiresAt)}.`;
    } catch (error) {
      if (error instanceof WidgetApiError && error.status === 401) {
        clearAuthState();
        state.authError = "Сессия истекла. Войдите снова.";
      } else {
        state.accountError = getErrorMessage(error);
      }
    } finally {
      state.accountBusy = false;
      render();
    }
  }

  async function exportPersonalData(): Promise<void> {
    if (!state.token) {
      state.authError = "Войдите, чтобы скачать данные аккаунта.";
      render();
      return;
    }

    state.accountBusy = true;
    state.accountError = null;
    render();

    try {
      const personalData = await api.exportPersonalData(state.token);
      downloadJson("cloudcomment-personal-data.json", personalData);
      state.notice = "Файл с персональными данными подготовлен.";
    } catch (error) {
      if (error instanceof WidgetApiError && error.status === 401) {
        clearAuthState();
        state.authError = "Сессия истекла. Войдите снова.";
      } else {
        state.accountError = getErrorMessage(error);
      }
    } finally {
      state.accountBusy = false;
      render();
    }
  }

  function clearAuthState(): void {
    state.token = null;
    state.userEmail = null;
    state.deleteConfirming = false;
    state.replyingTo = null;
    state.editingComment = null;
    state.confirmingCommentDeleteId = null;
    state.profileOpen = false;
    state.comments = clearViewerReactions(state.comments);
    removeStoredAuthToken();
  }

  async function submitComment(content: string): Promise<void> {
    if (!state.token) {
      state.authError = "Войдите или зарегистрируйтесь, чтобы оставить комментарий.";
      render();
      return;
    }

    const parentId = state.replyingTo?.id ?? null;
    const replyAuthor = state.replyingTo?.authorLabel ?? null;
    const previousComments = state.comments;
    const optimisticComment = createOptimisticComment(content, parentId);
    state.comments = parentId
      ? addReplyToRoot(state.comments, parentId, optimisticComment).comments
      : [optimisticComment, ...state.comments];
    state.submitting = true;
    state.error = null;
    state.notice = null;
    render();

    let submittedStatus: PublicComment["status"] | null = null;
    try {
      const createdComment = await api.createComment(content, parentId, state.token);
      submittedStatus = createdComment.status;
      const refreshedComments = await api.listComments(state.sort, state.token);
      state.comments = removeCommentFromTree(state.comments, optimisticComment.id);
      state.comments = parentId
        ? mergeCreatedReply(createdComment, refreshedComments.items, parentId, state.comments)
        : mergeCreatedComment(createdComment, refreshedComments.items);
      state.replyingTo = null;
      state.notice =
        createdComment.status === "APPROVED"
          ? "Комментарий опубликован."
          : "Комментарий отправлен и ждет модерации.";
      if (parentId) {
        state.notice = createdComment.status === "APPROVED"
          ? "Ответ опубликован."
          : `Ответ для ${replyAuthor ?? "комментария"} отправлен и ждет модерации.`;
      }
    } catch (error) {
      state.comments = previousComments;
      if (error instanceof WidgetApiError && error.status === 401) {
        clearAuthState();
      }
      state.error = getErrorMessage(error);
    } finally {
      if (submittedStatus) {
        state.notice = getSubmissionNotice(submittedStatus, parentId !== null, replyAuthor);
      }
      state.submitting = false;
      render();
    }
  }

  async function updateComment(commentId: string, content: string): Promise<void> {
    if (!state.token) {
      state.authError = "Войдите, чтобы редактировать свой комментарий.";
      render();
      return;
    }

    const previousComments = state.comments;
    const currentComment = findComment(state.comments, commentId);
    if (currentComment) {
      state.comments = updateCommentInTree(state.comments, {
        ...currentComment,
        content,
        updatedAt: new Date().toISOString()
      });
    }
    state.updatingCommentId = commentId;
    state.error = null;
    state.notice = null;
    render();

    try {
      const updatedComment = await api.updateComment(commentId, content, state.token);
      state.comments = updateCommentInTree(state.comments, updatedComment);
      state.editingComment = null;
      state.notice = updatedComment.status === "APPROVED"
        ? "Комментарий обновлен."
        : "Комментарий обновлен и отправлен на проверку.";
    } catch (error) {
      state.comments = previousComments;
      if (error instanceof WidgetApiError && error.status === 401) {
        clearAuthState();
      }
      state.error = getErrorMessage(error);
    } finally {
      state.updatingCommentId = null;
      render();
    }
  }

  async function deleteComment(commentId: string): Promise<void> {
    if (!state.token) {
      state.authError = "Войдите, чтобы удалить свой комментарий.";
      render();
      return;
    }

    const previousComments = state.comments;
    state.comments = removeCommentFromTree(state.comments, commentId);
    state.deletingCommentId = commentId;
    state.error = null;
    render();

    try {
      await api.deleteComment(commentId, state.token);
      state.confirmingCommentDeleteId = null;
      state.editingComment = state.editingComment?.id === commentId ? null : state.editingComment;
      state.notice = "Комментарий удален.";
    } catch (error) {
      state.comments = previousComments;
      if (error instanceof WidgetApiError && error.status === 401) {
        clearAuthState();
      }
      state.error = getErrorMessage(error);
    } finally {
      state.deletingCommentId = null;
      render();
    }
  }

  async function setReaction(commentId: string, type: CommentReactionType): Promise<void> {
    if (!state.token) {
      state.authError = "Войдите или зарегистрируйтесь, чтобы поставить реакцию.";
      render();
      return;
    }

    const currentReaction = findComment(state.comments, commentId)?.reactions.find((reaction) =>
      reaction.reactedByCurrentUser
    )?.type;
    const previousComments = state.comments;
    const currentComment = findComment(state.comments, commentId);
    if (currentComment) {
      state.comments = updateCommentReactions(
        state.comments,
        commentId,
        optimisticReactions(currentComment.reactions, currentReaction === type ? null : type)
      );
    }
    state.reactingCommentId = commentId;
    state.error = null;
    render();

    try {
      const response = await api.setReaction(commentId, currentReaction === type ? null : type, state.token);
      state.comments = updateCommentReactions(state.comments, commentId, response.reactions);
    } catch (error) {
      state.comments = previousComments;
      if (error instanceof WidgetApiError && error.status === 401) {
        clearAuthState();
        state.authError = "Сессия истекла. Войдите снова, чтобы поставить реакцию.";
      } else {
        state.error = getErrorMessage(error);
      }
    } finally {
      state.reactingCommentId = null;
      render();
    }
  }

  async function changeSort(sort: PublicCommentSort): Promise<void> {
    if (sort === state.sort) {
      return;
    }
    state.sort = sort;
    state.loading = true;
    state.error = null;
    render();
    try {
      state.comments = (await api.listComments(state.sort, state.token)).items;
    } catch (error) {
      state.error = getErrorMessage(error);
    } finally {
      state.loading = false;
      render();
    }
  }

  async function loadMoreReplies(commentId: string): Promise<void> {
    const comment = findComment(state.comments, commentId);
    if (!comment || state.loadingRepliesId) {
      return;
    }
    const nextPage = (state.replyPages[commentId] ?? 0) + 1;
    state.loadingRepliesId = commentId;
    state.error = null;
    render();
    try {
      const response = await api.listReplies(commentId, nextPage, 20, state.token);
      state.comments = updateRepliesInTree(state.comments, commentId, response.items, response.totalItems);
      state.replyPages = { ...state.replyPages, [commentId]: nextPage };
    } catch (error) {
      state.error = getErrorMessage(error);
    } finally {
      state.loadingRepliesId = null;
      render();
    }
  }

  shell.addEventListener("change", (event) => {
    const target = event.target;
    if (!(target instanceof HTMLSelectElement) || target.dataset.commentSort !== "true") {
      return;
    }
    if (isPublicCommentSort(target.value)) {
      void changeSort(target.value);
    }
  });

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
      return;
    }

    if (form.dataset.cloudCommentForm === "edit-comment") {
      const commentId = form.dataset.commentId;
      const formData = new FormData(form);
      const content = String(formData.get("comment") ?? "").trim();
      if (!commentId) {
        return;
      }
      if (!content) {
        state.error = "Комментарий не может быть пустым.";
        render();
        return;
      }
      void updateComment(commentId, content);
    }
  });

  shell.addEventListener("click", (event) => {
    const target = event.target;
    if (!(target instanceof Element)) {
      return;
    }

    if (!state.token && target.closest("[data-auth-intent='true']")) {
      state.authExpanded = true;
      state.authError = null;
      render();
      shell.querySelector<HTMLInputElement>("[data-cloud-comment-form='auth'] input[name='email']")?.focus();
      return;
    }

    const button = target.closest("button");
    if (!(button instanceof HTMLButtonElement) || !shell.contains(button)) {
      return;
    }

    if (button.dataset.authMode === "login" || button.dataset.authMode === "register") {
      state.authMode = button.dataset.authMode;
      state.authError = null;
      render();
      return;
    }

    if (button.dataset.authAction === "expand") {
      state.authExpanded = true;
      render();
      shell.querySelector<HTMLInputElement>("[data-cloud-comment-form='auth'] input[name='email']")?.focus();
      return;
    }

    if (button.dataset.profileAction === "toggle") {
      state.profileOpen = !state.profileOpen;
      render();
      return;
    }

    if (button.dataset.loadReplies) {
      void loadMoreReplies(button.dataset.loadReplies);
      return;
    }

    if (button.dataset.toggleReplies) {
      const next = new Set(state.collapsedReplyIds);
      if (next.has(button.dataset.toggleReplies)) {
        next.delete(button.dataset.toggleReplies);
      } else {
        next.add(button.dataset.toggleReplies);
      }
      state.collapsedReplyIds = next;
      render();
      return;
    }

    if (button.dataset.replyAction === "cancel") {
      state.replyingTo = null;
      render();
      return;
    }

    if (button.dataset.replyTo) {
      if (!state.token) {
        state.authExpanded = true;
        state.authError = "Войдите или зарегистрируйтесь, чтобы ответить.";
        render();
        return;
      }
      state.replyingTo = {
        id: button.dataset.replyTo,
        authorLabel: button.dataset.replyAuthor || "комментарий"
      };
      state.error = null;
      render();
      shell.querySelector<HTMLTextAreaElement>("[data-cloud-comment-form='comment'] textarea")?.focus();
      return;
    }

    if (button.dataset.commentAction === "edit" && button.dataset.commentId) {
      const comment = findComment(state.comments, button.dataset.commentId);
      if (comment?.ownedByCurrentUser) {
        state.editingComment = { id: comment.id, content: comment.content };
        state.confirmingCommentDeleteId = null;
        state.error = null;
        render();
      }
      return;
    }

    if (button.dataset.commentAction === "cancel-edit") {
      state.editingComment = null;
      render();
      return;
    }

    if (button.dataset.commentAction === "ask-delete" && button.dataset.commentId) {
      state.confirmingCommentDeleteId = button.dataset.commentId;
      render();
      return;
    }

    if (button.dataset.commentAction === "cancel-delete") {
      state.confirmingCommentDeleteId = null;
      render();
      return;
    }

    if (button.dataset.commentAction === "confirm-delete" && button.dataset.commentId) {
      void deleteComment(button.dataset.commentId);
      return;
    }

    if (button.dataset.reactionCommentId && isCommentReactionType(button.dataset.reactionType)) {
      if (!state.token) {
        state.authExpanded = true;
      }
      void setReaction(button.dataset.reactionCommentId, button.dataset.reactionType);
      return;
    }

    if (button.dataset.accountAction === "logout") {
      void logout();
      return;
    }

    if (button.dataset.accountAction === "delete") {
      state.deleteConfirming = true;
      state.accountError = null;
      render();
      return;
    }

    if (button.dataset.accountAction === "cancel-delete") {
      state.deleteConfirming = false;
      state.accountError = null;
      render();
      return;
    }

    if (button.dataset.accountAction === "request-delete") {
      void requestAccountDeletion();
      return;
    }

    if (button.dataset.accountAction === "export-data") {
      void exportPersonalData();
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

function createThemeController(
  shell: HTMLElement,
  theme: WidgetTheme
): { setConfiguredTheme: (theme: WidgetStyleTheme) => void; destroy: () => void } {
  let configuredTheme: WidgetStyleTheme = "AUTO";

  const applyTheme = () => {
    shell.dataset.theme = resolveWidgetTheme(theme, configuredTheme);
  };

  const setConfiguredTheme = (nextTheme: WidgetStyleTheme) => {
    configuredTheme = nextTheme;
    applyTheme();
  };

  applyTheme();

  if (theme !== "auto") {
    return { setConfiguredTheme, destroy: () => undefined };
  }

  const mediaQuery = window.matchMedia("(prefers-color-scheme: dark)");
  const observer = new MutationObserver(applyTheme);
  observer.observe(document.documentElement, { attributes: true, attributeFilter: ["class", "data-theme", "style"] });

  if (document.body) {
    observer.observe(document.body, { attributes: true, attributeFilter: ["class", "data-theme", "style"] });
  }

  mediaQuery.addEventListener("change", applyTheme);

  return {
    setConfiguredTheme,
    destroy: () => {
      observer.disconnect();
      mediaQuery.removeEventListener("change", applyTheme);
    }
  };
}

function applyWidgetStyle(shell: HTMLElement, style: WidgetStyle): void {
  shell.style.setProperty("--cc-custom-accent", style.accentColor);
  shell.style.setProperty("--cc-custom-accent-contrast", readableTextColor(style.accentColor));
  shell.dataset.radius = style.cornerRadius.toLowerCase();
  shell.dataset.density = style.density.toLowerCase();
  shell.dataset.elevation = style.elevation.toLowerCase();
  shell.dataset.fontFamily = style.fontFamily.toLowerCase();
  shell.dataset.fontScale = style.fontScale.toLowerCase();
  shell.dataset.alignment = style.alignment.toLowerCase();
  shell.dataset.contentWidth = style.contentWidth.toLowerCase();
  shell.lang = style.locale.toLowerCase();
  shell.setAttribute("aria-label", `${style.commentsTitle} CloudComment`);
}

function resolveWidgetTheme(theme: WidgetTheme, configuredTheme: WidgetStyleTheme): ResolvedWidgetTheme {
  if (theme === "light" || theme === "dark") {
    return theme;
  }

  if (configuredTheme === "LIGHT" || configuredTheme === "DARK") {
    return configuredTheme.toLowerCase() as ResolvedWidgetTheme;
  }

  return detectHostTheme() ?? (window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light");
}

function readableTextColor(hexColor: string): string {
  const match = /^#([0-9a-fA-F]{6})$/.exec(hexColor);
  if (!match) {
    return "#ffffff";
  }

  const value = Number.parseInt(match[1], 16);
  const red = (value >> 16) & 255;
  const green = (value >> 8) & 255;
  const blue = value & 255;
  const luminance = (0.2126 * red + 0.7152 * green + 0.0722 * blue) / 255;
  return luminance > 0.62 ? "#10201d" : "#ffffff";
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
): HTMLElement | null {
  const style = state.config?.style;
  if (style && !style.showHeader) {
    return null;
  }
  const header = document.createElement("header");
  header.className = "cloud-comment__header";

  const titleBlock = document.createElement("div");

  const eyebrow = document.createElement("p");
  eyebrow.className = "cloud-comment__eyebrow";
  eyebrow.textContent = "Обсуждение";

  const title = document.createElement("h2");
  title.className = "cloud-comment__title";
  title.textContent = style?.headerTitle ?? "Комментарии";

  titleBlock.append(eyebrow, title);

  const badge = document.createElement("span");
  badge.className = "cloud-comment__badge";
  badge.textContent = "CloudComment";

  header.append(titleBlock, badge);
  return header;
}

function renderBody(state: WidgetState, options: Required<CloudCommentWidgetOptions>): HTMLElement {
  const body = document.createElement("div");
  body.className = "cloud-comment__body";

  if (state.error) {
    body.append(renderMessage(state.error, "error"));
  }

  if (state.notice) {
    body.append(renderMessage(state.notice, "notice"));
  }

  const style = state.config?.style;
  const form = renderCommentForm(state);
  if (style?.composerPosition === "TOP") {
    body.append(form);
  }

  if (state.loading) {
    body.append(renderMessage("Загружаем комментарии...", "muted"));
  } else {
    if (style?.showSort ?? true) {
      body.append(renderCommentSort(state.sort));
    }
    body.append(renderCommentList(state.comments, state));
  }

  if (style?.composerPosition !== "TOP") {
    body.append(form);
  }
  body.append(renderAccountSection(state, options), renderAuthSection(state, options));
  return body;
}

function renderCommentSort(sort: PublicCommentSort): HTMLElement {
  const control = document.createElement("label");
  control.className = "cloud-comment__sort";

  const label = document.createElement("span");
  label.textContent = "Сортировка";

  const select = document.createElement("select");
  select.dataset.commentSort = "true";
  select.setAttribute("aria-label", "Сортировка комментариев");
  const options: Array<[PublicCommentSort, string]> = [
    ["PINNED_FIRST", "Закреплённые сначала"],
    ["NEWEST", "Сначала новые"],
    ["OLDEST", "Сначала старые"],
    ["TOP_REACTIONS", "По реакциям"]
  ];
  for (const [value, text] of options) {
    const option = document.createElement("option");
    option.value = value;
    option.textContent = text;
    option.selected = value === sort;
    select.append(option);
  }

  control.append(label, select);
  return control;
}

function renderCommentList(comments: PublicComment[], state: WidgetState): HTMLElement {
  const list = document.createElement("div");
  list.className = "cloud-comment__list";

  if (comments.length === 0) {
    const empty = document.createElement("p");
    empty.className = "cloud-comment__empty";
    empty.textContent = state.config?.style.emptyMessage ?? "Пока нет комментариев. Будьте первым, кто начнет обсуждение.";
    list.append(empty);
    return list;
  }

  for (const comment of comments) {
    list.append(renderComment(comment, state, 0));
  }

  return list;
}

function renderComment(comment: PublicComment, state: WidgetState, depth: number): HTMLElement {
  const article = document.createElement("article");
  article.className = "cloud-comment__comment";
  const isEditing = state.editingComment?.id === comment.id;

  const header = document.createElement("header");
  header.className = "cloud-comment__comment-header";

  const authorLabel = getAuthorLabel(comment);

  const avatar = document.createElement("span");
  avatar.className = "cloud-comment__avatar";
  avatar.textContent = getInitials(authorLabel);

  const author = document.createElement("strong");
  author.textContent = authorLabel;

  const date = document.createElement("time");
  date.dateTime = comment.createdAt;
  date.textContent = formatDate(comment.createdAt);

  if (state.config?.style.avatarStyle !== "HIDDEN") {
    header.append(avatar);
  }
  header.append(author, date);

  if (comment.status !== "APPROVED") {
    const status = document.createElement("span");
    status.className = "cloud-comment__status";
    status.textContent = getStatusLabel(comment.status);
    header.append(status);
  }

  if (depth === 0 && comment.pinned) {
    const pinned = document.createElement("span");
    pinned.className = "cloud-comment__pinned";
    pinned.textContent = "Закреплён";
    header.append(pinned);
  }

  const content = document.createElement("p");
  content.className = "cloud-comment__comment-content";
  content.textContent = comment.content;

  const reactions = renderReactionBar(comment, state);

  const footer = document.createElement("footer");
  footer.className = "cloud-comment__comment-footer";

  if (!isEditing && depth === 0 && comment.status === "APPROVED") {
    const replyButton = document.createElement("button");
    replyButton.className = "cloud-comment__reply-button";
    replyButton.type = "button";
    replyButton.dataset.replyTo = comment.id;
    replyButton.dataset.replyAuthor = authorLabel;
    replyButton.textContent = state.replyingTo?.id === comment.id ? "Отвечаем" : "Ответить";
    replyButton.disabled = state.submitting;
    footer.append(replyButton);
  }

  if (!isEditing && comment.ownedByCurrentUser) {
    footer.append(...renderOwnerCommentActions(comment, state));
  }

  if (isEditing) {
    article.append(header, renderEditCommentForm(comment, state));
  } else {
    article.append(header, content, reactions);
  }

  if (footer.childNodes.length > 0) {
    article.append(footer);
  }

  if (state.confirmingCommentDeleteId === comment.id) {
    article.append(renderDeleteCommentConfirm(comment, state));
  }

  const repliesCollapsed = state.collapsedReplyIds.has(comment.id);
  if (depth === 0 && comment.replies.length > 0) {
    const toggleReplies = document.createElement("button");
    toggleReplies.type = "button";
    toggleReplies.className = "cloud-comment__load-replies";
    toggleReplies.dataset.toggleReplies = comment.id;
    toggleReplies.setAttribute("aria-expanded", String(!repliesCollapsed));
    toggleReplies.textContent = repliesCollapsed
      ? `Показать ответы (${comment.replyCount ?? comment.replies.length})`
      : "Скрыть ответы";
    article.append(toggleReplies);
  }

  if (depth === 0 && comment.replies.length > 0 && !repliesCollapsed) {
    const replies = document.createElement("div");
    replies.className = "cloud-comment__replies";
    for (const reply of comment.replies) {
      replies.append(renderComment(reply, state, depth + 1));
    }
    article.append(replies);
  }

  if (depth === 0 && !repliesCollapsed && (comment.replyCount ?? comment.replies.length) > comment.replies.length) {
    const remaining = (comment.replyCount ?? comment.replies.length) - comment.replies.length;
    const loadReplies = document.createElement("button");
    loadReplies.type = "button";
    loadReplies.className = "cloud-comment__load-replies";
    loadReplies.dataset.loadReplies = comment.id;
    loadReplies.disabled = state.loadingRepliesId === comment.id;
    loadReplies.textContent = state.loadingRepliesId === comment.id
      ? "Загружаем ответы..."
      : `Показать ещё ответы (${remaining})`;
    article.append(loadReplies);
  }

  return article;
}

function renderOwnerCommentActions(comment: PublicComment, state: WidgetState): HTMLButtonElement[] {
  const editButton = document.createElement("button");
  editButton.className = "cloud-comment__comment-action";
  editButton.type = "button";
  editButton.dataset.commentAction = "edit";
  editButton.dataset.commentId = comment.id;
  editButton.disabled = state.submitting || state.updatingCommentId === comment.id || state.deletingCommentId === comment.id;
  editButton.textContent = "Редактировать";

  const deleteButton = document.createElement("button");
  deleteButton.className = "cloud-comment__comment-action cloud-comment__comment-action--danger";
  deleteButton.type = "button";
  deleteButton.dataset.commentAction = "ask-delete";
  deleteButton.dataset.commentId = comment.id;
  deleteButton.disabled = state.deletingCommentId === comment.id;
  deleteButton.textContent = state.deletingCommentId === comment.id ? "Удаляем..." : "Удалить";

  return [editButton, deleteButton];
}

function renderEditCommentForm(comment: PublicComment, state: WidgetState): HTMLFormElement {
  const form = document.createElement("form");
  form.className = "cloud-comment__edit-form";
  form.dataset.cloudCommentForm = "edit-comment";
  form.dataset.commentId = comment.id;

  const textarea = document.createElement("textarea");
  textarea.className = "cloud-comment__textarea";
  textarea.name = "comment";
  textarea.maxLength = 5000;
  textarea.value = state.editingComment?.content ?? comment.content;
  textarea.disabled = state.updatingCommentId === comment.id;
  textarea.setAttribute("aria-label", "Редактировать комментарий");

  const actions = document.createElement("div");
  actions.className = "cloud-comment__inline-actions";

  const cancel = document.createElement("button");
  cancel.type = "button";
  cancel.className = "cloud-comment__reply-cancel";
  cancel.dataset.commentAction = "cancel-edit";
  cancel.disabled = state.updatingCommentId === comment.id;
  cancel.textContent = "Отмена";

  const save = document.createElement("button");
  save.type = "submit";
  save.className = "cloud-comment__button";
  save.disabled = state.updatingCommentId === comment.id;
  save.textContent = state.updatingCommentId === comment.id ? "Сохраняем..." : "Сохранить";

  actions.append(cancel, save);
  form.append(textarea, actions);
  return form;
}

function renderDeleteCommentConfirm(comment: PublicComment, state: WidgetState): HTMLElement {
  const confirm = document.createElement("div");
  confirm.className = "cloud-comment__delete-comment-confirm";

  const text = document.createElement("span");
  text.textContent = "Удалить комментарий? Он исчезнет из публичного обсуждения.";

  const actions = document.createElement("div");
  actions.className = "cloud-comment__inline-actions";

  const cancel = document.createElement("button");
  cancel.type = "button";
  cancel.className = "cloud-comment__reply-cancel";
  cancel.dataset.commentAction = "cancel-delete";
  cancel.disabled = state.deletingCommentId === comment.id;
  cancel.textContent = "Отмена";

  const submit = document.createElement("button");
  submit.type = "button";
  submit.className = "cloud-comment__comment-action cloud-comment__comment-action--danger";
  submit.dataset.commentAction = "confirm-delete";
  submit.dataset.commentId = comment.id;
  submit.disabled = state.deletingCommentId === comment.id;
  submit.textContent = state.deletingCommentId === comment.id ? "Удаляем..." : "Да, удалить";

  actions.append(cancel, submit);
  confirm.append(text, actions);
  return confirm;
}

function renderReactionBar(comment: PublicComment, state: WidgetState): HTMLElement {
  const bar = document.createElement("div");
  bar.className = "cloud-comment__reactions";

  const enabled = new Set(state.config?.style.enabledReactions ?? ["LIKE", "LOVE", "LAUGH", "WOW"]);
  for (const reaction of normalizeReactions(comment.reactions).filter((item) => enabled.has(item.type))) {
    const button = document.createElement("button");
    button.className = reaction.reactedByCurrentUser
      ? "cloud-comment__reaction cloud-comment__reaction--active"
      : "cloud-comment__reaction";
    button.type = "button";
    button.dataset.reactionCommentId = comment.id;
    button.dataset.reactionType = reaction.type;
    button.disabled = state.reactingCommentId === comment.id;
    button.setAttribute("aria-label", reaction.label);

    const emoji = document.createElement("span");
    emoji.className = "cloud-comment__reaction-emoji";
    emoji.textContent = reaction.emoji;

    const count = document.createElement("span");
    count.className = "cloud-comment__reaction-count";
    count.textContent = String(reaction.count);

    button.append(emoji, count);
    bar.append(button);
  }

  return bar;
}

function renderCommentForm(state: WidgetState): HTMLElement {
  const form = document.createElement("form");
  form.className = "cloud-comment__form";
  form.dataset.cloudCommentForm = "comment";

  if (state.replyingTo) {
    const replyContext = document.createElement("div");
    replyContext.className = "cloud-comment__reply-context";

    const replyText = document.createElement("span");
    replyText.textContent = `Ответ для ${state.replyingTo.authorLabel}`;

    const cancelReply = document.createElement("button");
    cancelReply.type = "button";
    cancelReply.className = "cloud-comment__reply-cancel";
    cancelReply.dataset.replyAction = "cancel";
    cancelReply.textContent = "Отмена";
    cancelReply.disabled = state.submitting;

    replyContext.append(replyText, cancelReply);
    form.append(replyContext);
  }

  const textarea = document.createElement("textarea");
  textarea.className = "cloud-comment__textarea";
  textarea.name = "comment";
  textarea.placeholder = state.token
    ? state.config?.style.composerPlaceholder ?? "Напишите комментарий"
    : "Войдите, чтобы написать комментарий";
  textarea.setAttribute("aria-label", "Написать комментарий");
  if (state.replyingTo) {
    textarea.placeholder = "Напишите ответ";
  }
  textarea.maxLength = 5000;
  textarea.disabled = state.submitting;
  textarea.readOnly = !state.token;
  if (!state.token) {
    textarea.dataset.authIntent = "true";
  }

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

function renderAccountSection(
  state: WidgetState,
  options: Required<CloudCommentWidgetOptions>
): HTMLElement {
  const section = document.createElement("section");
  section.className = "cloud-comment__account";

  if (!state.token) {
    section.hidden = true;
    return section;
  }

  const summary = document.createElement("button");
  summary.className = "cloud-comment__account-summary";
  summary.type = "button";
  summary.dataset.profileAction = "toggle";
  summary.setAttribute("aria-expanded", String(state.profileOpen));
  summary.setAttribute("aria-label", "Меню профиля");

  const avatar = document.createElement("span");
  avatar.className = "cloud-comment__avatar cloud-comment__avatar--account";
  avatar.textContent = getInitials(state.userEmail ?? "CC");

  const text = document.createElement("div");
  text.className = "cloud-comment__account-text";

  const title = document.createElement("strong");
  title.textContent = state.userEmail ?? "Аккаунт комментатора";

  const caption = document.createElement("span");
  caption.textContent = "Управление аккаунтом и персональными данными";

  text.append(title, caption);
  summary.append(avatar, text);

  const actions = document.createElement("div");
  actions.className = "cloud-comment__account-actions";
  actions.append(
    renderAccountButton("Скачать данные", "export-data", state.accountBusy),
    renderAccountButton("Выйти", "logout", state.accountBusy),
    renderAccountButton("Удалить аккаунт", "delete", state.accountBusy, true)
  );

  const links = document.createElement("div");
  links.className = "cloud-comment__account-links";
  if (state.consentRequirements) {
    links.append(
      createAccountLink("Политика", state.consentRequirements.privacyPolicyUrl, options.apiBaseUrl),
      createAccountLink("Условия", state.consentRequirements.termsUrl, options.apiBaseUrl),
      createAccountLink("Персональные данные", state.consentRequirements.personalDataNoticeUrl, options.apiBaseUrl)
    );
  }

  section.append(summary);
  if (!state.profileOpen) {
    return section;
  }
  section.append(actions);
  if (links.childNodes.length > 0) {
    section.append(links);
  }

  if (state.accountError) {
    section.append(renderMessage(state.accountError, "error"));
  }

  if (state.deleteConfirming) {
    const confirmation = document.createElement("div");
    confirmation.className = "cloud-comment__delete-confirm";

    const message = document.createElement("p");
    message.textContent = "Мы отправим письмо с одноразовой ссылкой и кодом. Аккаунт удалится только после подтверждения.";

    const confirmationActions = document.createElement("div");
    confirmationActions.className = "cloud-comment__delete-actions";
    confirmationActions.append(
      renderAccountButton("Отправить письмо", "request-delete", state.accountBusy, true),
      renderAccountButton("Отмена", "cancel-delete", state.accountBusy)
    );

    confirmation.append(message, confirmationActions);
    section.append(confirmation);
  }

  return section;
}

function renderAccountButton(
  label: string,
  action: string,
  disabled: boolean,
  danger = false
): HTMLButtonElement {
  const button = document.createElement("button");
  button.type = "button";
  button.className = danger
    ? "cloud-comment__account-button cloud-comment__account-button--danger"
    : "cloud-comment__account-button";
  button.dataset.accountAction = action;
  button.disabled = disabled;
  button.textContent = label;
  return button;
}

function createAccountLink(label: string, href: string, apiBaseUrl: string): HTMLAnchorElement {
  const link = document.createElement("a");
  link.href = toCloudCommentUrl(href, apiBaseUrl);
  link.target = "_blank";
  link.rel = "noreferrer";
  link.textContent = label;
  return link;
}

function renderAuthSection(state: WidgetState, options: Required<CloudCommentWidgetOptions>): HTMLElement {
  const section = document.createElement("section");
  section.className = "cloud-comment__auth";

  if (state.token) {
    section.hidden = true;
    return section;
  }

  if (!state.authExpanded) {
    const expand = document.createElement("button");
    expand.type = "button";
    expand.className = "cloud-comment__auth-expand";
    expand.dataset.authAction = "expand";
    expand.textContent = "Войти, чтобы участвовать";
    section.append(expand);
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
      privacyLink.href = toCloudCommentUrl(state.consentRequirements.privacyPolicyUrl, options.apiBaseUrl);
      privacyLink.target = "_blank";
      privacyLink.rel = "noreferrer";
      privacyLink.textContent = "политика";

      const termsLink = document.createElement("a");
      termsLink.href = toCloudCommentUrl(state.consentRequirements.termsUrl, options.apiBaseUrl);
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

function getAuthorLabel(comment: PublicComment): string {
  return getPublicAuthorLabel(comment.author);
}

export function getPublicAuthorLabel(author: CommentAuthor): string {
  const displayName = author.displayName?.trim();
  return !displayName || displayName.includes("@") ? "Участник" : displayName;
}

function mergeCreatedReply(
  createdComment: PublicComment,
  refreshedComments: PublicComment[],
  parentId: string,
  previousComments: PublicComment[]
): PublicComment[] {
  const merged = addReplyToRoot(refreshedComments, parentId, createdComment, true);
  if (merged.added) {
    return merged.comments;
  }
  return addReplyToRoot(previousComments, parentId, createdComment).comments;
}

function normalizeWidgetStyle(style: WidgetStyle): WidgetStyle {
  return {
    version: style.version ?? 2,
    theme: style.theme ?? "AUTO",
    accentColor: style.accentColor ?? "#0f766e",
    cornerRadius: style.cornerRadius ?? "MEDIUM",
    density: style.density ?? "COMFORTABLE",
    contentWidth: style.contentWidth ?? "READABLE",
    alignment: style.alignment ?? "CENTER",
    fontScale: style.fontScale ?? "MEDIUM",
    fontFamily: style.fontFamily ?? "INHERIT",
    showHeader: style.showHeader ?? true,
    headerTitle: style.headerTitle ?? "Комментарии",
    composerPosition: style.composerPosition ?? "BOTTOM",
    defaultSort: style.defaultSort ?? "PINNED_FIRST",
    showSort: style.showSort ?? true,
    enabledReactions: style.enabledReactions ?? ["LIKE", "LOVE", "LAUGH", "WOW"],
    avatarStyle: style.avatarStyle ?? "INITIALS",
    elevation: style.elevation ?? "BORDER",
    locale: style.locale ?? "RU",
    commentsTitle: style.commentsTitle ?? "Комментарии",
    composerPlaceholder: style.composerPlaceholder ?? "Напишите комментарий",
    emptyMessage: style.emptyMessage ?? "Пока нет комментариев. Будьте первым, кто начнет обсуждение."
  };
}

function createOptimisticComment(
  content: string,
  parentId: string | null
): PublicComment {
  const now = new Date().toISOString();
  return {
    id: `optimistic-${crypto.randomUUID()}`,
    siteId: "",
    pageId: "",
    parentId,
    author: { id: "", displayName: "Вы" },
    content,
    status: "PENDING",
    createdAt: now,
    updatedAt: now,
    editedAt: null,
    pinned: false,
    ownedByCurrentUser: true,
    reactions: normalizeReactions([]),
    replyCount: 0,
    replies: []
  };
}

export function optimisticReactions(
  reactions: CommentReaction[],
  nextType: CommentReactionType | null
): CommentReaction[] {
  return normalizeReactions(reactions).map((reaction) => {
    const wasActive = reaction.reactedByCurrentUser;
    const becomesActive = reaction.type === nextType;
    return {
      ...reaction,
      count: Math.max(0, reaction.count + (becomesActive ? 1 : 0) - (wasActive ? 1 : 0)),
      reactedByCurrentUser: becomesActive
    };
  });
}

export function updateRepliesInTree(
  comments: PublicComment[],
  commentId: string,
  replies: PublicComment[],
  replyCount: number
): PublicComment[] {
  return comments.map((comment) => {
    if (comment.id === commentId) {
      const known = new Map(comment.replies.map((reply) => [reply.id, reply]));
      for (const reply of replies) {
        known.set(reply.id, reply);
      }
      return { ...comment, replyCount, replies: [...known.values()] };
    }
    return comment.replies.length === 0
      ? comment
      : { ...comment, replies: updateRepliesInTree(comment.replies, commentId, replies, replyCount) };
  });
}

function addReplyToRoot(
  comments: PublicComment[],
  parentId: string,
  reply: PublicComment,
  countAlreadyIncludesReply = false
): { comments: PublicComment[]; added: boolean } {
  let added = false;
  const nextComments = comments.map((comment) => {
    if (comment.id !== parentId) {
      return comment;
    }
    if (comment.replies.some((existingReply) => existingReply.id === reply.id)) {
      added = true;
      return comment;
    }
    added = true;
    return {
      ...comment,
      replyCount: (comment.replyCount ?? comment.replies.length) + (countAlreadyIncludesReply ? 0 : 1),
      replies: [...comment.replies, reply]
    };
  });
  return { comments: nextComments, added };
}

function mergeCreatedComment(createdComment: PublicComment, comments: PublicComment[]): PublicComment[] {
  if (comments.some((comment) => comment.id === createdComment.id)) {
    return comments;
  }

  return [createdComment, ...comments];
}

function updateCommentInTree(comments: PublicComment[], updatedComment: PublicComment): PublicComment[] {
  return comments.map((comment) => {
    if (comment.id === updatedComment.id) {
      return {
        ...updatedComment,
        replyCount: comment.replyCount,
        replies: comment.replies
      };
    }
    if (comment.replies.length === 0) {
      return comment;
    }
    return {
      ...comment,
      replies: updateCommentInTree(comment.replies, updatedComment)
    };
  });
}

function removeCommentFromTree(comments: PublicComment[], commentId: string): PublicComment[] {
  return comments
    .filter((comment) => comment.id !== commentId)
    .map((comment) => {
      const removedDirectReply = comment.replies.some((reply) => reply.id === commentId);
      return {
        ...comment,
        replyCount: removedDirectReply ? Math.max(0, comment.replyCount - 1) : comment.replyCount,
        replies: removeCommentFromTree(comment.replies, commentId)
      };
    });
}

function updateCommentReactions(
  comments: PublicComment[],
  commentId: string,
  reactions: CommentReaction[]
): PublicComment[] {
  return comments.map((comment) => {
    if (comment.id === commentId) {
      return {
        ...comment,
        reactions: normalizeReactions(reactions)
      };
    }
    if (comment.replies.length === 0) {
      return comment;
    }
    return {
      ...comment,
      replies: updateCommentReactions(comment.replies, commentId, reactions)
    };
  });
}

function clearViewerReactions(comments: PublicComment[]): PublicComment[] {
  return comments.map((comment) => ({
    ...comment,
    reactions: normalizeReactions(comment.reactions).map((reaction) => ({
      ...reaction,
      reactedByCurrentUser: false
    })),
    replies: clearViewerReactions(comment.replies)
  }));
}

function findComment(comments: PublicComment[], commentId: string): PublicComment | null {
  for (const comment of comments) {
    if (comment.id === commentId) {
      return comment;
    }
    const reply = findComment(comment.replies, commentId);
    if (reply) {
      return reply;
    }
  }
  return null;
}

function normalizeReactions(reactions: CommentReaction[] | undefined): CommentReaction[] {
  const byType = new Map((reactions ?? []).map((reaction) => [reaction.type, reaction]));
  return (["LIKE", "LOVE", "LAUGH", "WOW"] as const).map((type) => byType.get(type) ?? defaultReaction(type));
}

function defaultReaction(type: CommentReactionType): CommentReaction {
  return {
    type,
    emoji: reactionEmoji(type),
    label: reactionLabel(type),
    count: 0,
    reactedByCurrentUser: false
  };
}

function reactionEmoji(type: CommentReactionType): string {
  return {
    LIKE: "👍",
    LOVE: "❤️",
    LAUGH: "😂",
    WOW: "😮"
  }[type];
}

function reactionLabel(type: CommentReactionType): string {
  return {
    LIKE: "Нравится",
    LOVE: "Люблю",
    LAUGH: "Смешно",
    WOW: "Удивительно"
  }[type];
}

function isCommentReactionType(value: string | undefined): value is CommentReactionType {
  return value === "LIKE" || value === "LOVE" || value === "LAUGH" || value === "WOW";
}

function isPublicCommentSort(value: string): value is PublicCommentSort {
  return ["PINNED_FIRST", "NEWEST", "OLDEST", "TOP_REACTIONS"].includes(value);
}

function toCloudCommentUrl(href: string, apiBaseUrl: string): string {
  try {
    return new URL(href, apiBaseUrl).toString();
  } catch {
    return href;
  }
}

function downloadJson(fileName: string, value: unknown): void {
  const blob = new Blob([JSON.stringify(value, null, 2)], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName;
  link.style.display = "none";
  document.body.append(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
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

function getSubmissionNotice(
  status: PublicComment["status"],
  isReply: boolean,
  replyAuthor: string | null
): string {
  if (status === "APPROVED") {
    return isReply ? "Ответ опубликован." : "Комментарий опубликован.";
  }

  if (isReply) {
    return `Ответ для ${replyAuthor ?? "комментария"} отправлен на проверку.`;
  }

  return "Комментарий отправлен на проверку.";
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
    readStorage("localStorage", WIDGET_AUTH_TOKEN_KEY) ??
    readStorage("sessionStorage", WIDGET_AUTH_TOKEN_KEY) ??
    readStorage("localStorage", ADMIN_AUTH_TOKEN_KEY) ??
    readStorage("sessionStorage", ADMIN_AUTH_TOKEN_KEY)
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
    window.sessionStorage.removeItem(WIDGET_AUTH_TOKEN_KEY);
    window.localStorage.removeItem(ADMIN_AUTH_TOKEN_KEY);
    window.sessionStorage.removeItem(ADMIN_AUTH_TOKEN_KEY);
  } catch {
    // Ignore storage cleanup failures on embedded pages.
  }
}

function readStorage(storageName: "localStorage" | "sessionStorage", key: string): string | null {
  try {
    return window[storageName].getItem(key);
  } catch {
    return null;
  }
}
