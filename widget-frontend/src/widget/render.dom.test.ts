// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { WIDGET_AUTH_TOKEN_KEY } from "./config";
import { renderWidget } from "./render";
import type { PublicComment, PublicWidgetConfig } from "./types";

const siteId = "00000000-0000-0000-0000-000000000001";
const token = "widget-test-token";

const config: PublicWidgetConfig = {
  siteId,
  moderationMode: "POST_MODERATION",
  style: {
    version: 2,
    theme: "LIGHT",
    accentColor: "#0f766e",
    cornerRadius: "MEDIUM",
    density: "COMFORTABLE",
    contentWidth: "READABLE",
    alignment: "CENTER",
    fontScale: "MEDIUM",
    fontFamily: "INHERIT",
    showHeader: true,
    headerTitle: "Комментарии",
    composerPosition: "BOTTOM",
    defaultSort: "PINNED_FIRST",
    showSort: true,
    enabledReactions: ["LIKE", "LOVE", "LAUGH", "WOW"],
    avatarStyle: "INITIALS",
    elevation: "BORDER",
    locale: "RU",
    commentsTitle: "Комментарии",
    composerPlaceholder: "Напишите комментарий",
    emptyMessage: "Комментариев пока нет"
  }
};

function publicComment(overrides: Partial<PublicComment> = {}): PublicComment {
  return {
    id: "00000000-0000-0000-0000-000000000010",
    siteId,
    pageId: "00000000-0000-0000-0000-000000000020",
    parentId: null,
    author: { id: "user-id", displayName: "Анна" },
    content: "Исходный комментарий",
    status: "APPROVED",
    createdAt: "2026-07-13T10:00:00Z",
    updatedAt: "2026-07-13T10:00:00Z",
    editedAt: null,
    pinned: false,
    ownedByCurrentUser: true,
    reactions: [
      { type: "LIKE", emoji: "👍", label: "Нравится", count: 0, reactedByCurrentUser: false },
      { type: "LOVE", emoji: "❤️", label: "Люблю", count: 0, reactedByCurrentUser: false },
      { type: "LAUGH", emoji: "😂", label: "Смешно", count: 0, reactedByCurrentUser: false },
      { type: "WOW", emoji: "😮", label: "Удивительно", count: 0, reactedByCurrentUser: false }
    ],
    replyCount: 0,
    replies: [],
    ...overrides
  };
}

function rootComments(count: number, label = "Комментарий"): PublicComment[] {
  return Array.from({ length: count }, (_, index) => publicComment({
    id: `00000000-0000-0000-0001-${String(index + 1).padStart(12, "0")}`,
    content: `${label} ${index + 1}`,
    ownedByCurrentUser: false
  }));
}

type ApiOverrides = {
  onList?: (url: URL) => Promise<Response> | Response;
  onPost?: () => Promise<Response> | Response;
  onPatch?: () => Promise<Response> | Response;
  onDelete?: () => Promise<Response> | Response;
  onReaction?: () => Promise<Response> | Response;
  onLogout?: () => Promise<Response> | Response;
  failListAfterPost?: boolean;
  loginEmail?: string;
  commentsAfterLogin?: PublicComment[];
};

function installApiMock(overrides: ApiOverrides = {}) {
  let listCalls = 0;
  let postCompleted = false;
  let loginCompleted = false;

  const fetchMock = vi.fn(async (input: string | URL | Request, init?: RequestInit): Promise<Response> => {
    const url = String(input);
    const method = (init?.method ?? "GET").toUpperCase();

    if (url.endsWith(`/public/sites/${siteId}/config`)) {
      return jsonResponse(config);
    }
    if (url.endsWith("/privacy/consent-requirements")) {
      return jsonResponse({
        privacyPolicyVersion: "test",
        termsVersion: "test",
        privacyPolicyUrl: "/legal/privacy",
        termsUrl: "/legal/terms",
        personalDataNoticeUrl: "/legal/personal-data",
        dataExportInfoUrl: "/legal/export"
      });
    }
    if (url.endsWith(`/public/sites/${siteId}/auth/me`)) {
      return jsonResponse({
        id: "user-id",
        email: "anna@example.test",
        roles: ["COMMENTER"],
        createdAt: "2026-07-13T10:00:00Z",
        updatedAt: "2026-07-13T10:00:00Z"
      });
    }
    if (url.endsWith(`/public/sites/${siteId}/auth/logout`) && method === "POST") {
      if (overrides.onLogout) {
        return overrides.onLogout();
      }
      return new Response(null, { status: 204 });
    }
    if (url.endsWith(`/public/sites/${siteId}/auth/login`) && method === "POST") {
      loginCompleted = true;
      return jsonResponse({
        token: "new-widget-token",
        tokenType: "Bearer",
        expiresAt: "2026-07-14T10:00:00Z",
        user: {
          id: "new-user-id",
          email: overrides.loginEmail ?? "new-user@example.test",
          roles: ["COMMENTER"],
          createdAt: "2026-07-13T11:00:00Z",
          updatedAt: "2026-07-13T11:00:00Z"
        }
      });
    }
    if (url.includes(`/public/sites/${siteId}/pages/comments`) && method === "GET") {
      listCalls += 1;
      if (overrides.onList) {
        return overrides.onList(new URL(url));
      }
      if (postCompleted && overrides.failListAfterPost) {
        return jsonResponse({ error: { message: "Не удалось обновить список" } }, 500);
      }
      const comments = loginCompleted ? overrides.commentsAfterLogin ?? [publicComment()] : [publicComment()];
      return jsonResponse({ items: comments, page: 1, pageSize: 20, totalItems: comments.length, totalPages: 1 });
    }
    if (url.endsWith(`/public/sites/${siteId}/pages/comments`) && method === "POST") {
      const requestBody = init?.body ? JSON.parse(String(init.body)) as { content?: string } : {};
      const response = await (overrides.onPost?.() ?? jsonResponse(publicComment({
        id: "created-comment",
        content: requestBody.content ?? "Новый комментарий"
      }), 201));
      if (response.ok) {
        postCompleted = true;
      }
      return response;
    }
    if (url.includes(`/public/sites/${siteId}/comments/`) && method === "PATCH") {
      return overrides.onPatch?.() ?? jsonResponse(publicComment({ content: "Обновлённый комментарий" }));
    }
    if (url.includes(`/public/sites/${siteId}/comments/`) && method === "DELETE") {
      return overrides.onDelete?.() ?? new Response(null, { status: 204 });
    }
    if (url.endsWith("/reaction") && method === "PUT") {
      if (overrides.onReaction) {
        return overrides.onReaction();
      }
      return jsonResponse({
        reactions: publicComment().reactions.map((reaction) => ({
          ...reaction,
          count: reaction.type === "LIKE" ? 1 : 0,
          reactedByCurrentUser: reaction.type === "LIKE"
        }))
      });
    }

    throw new Error(`Неожиданный запрос ${method} ${url}`);
  });

  vi.stubGlobal("fetch", fetchMock);
  return { fetchMock, getListCalls: () => listCalls };
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" }
  });
}

function deferredResponse(): { promise: Promise<Response>; resolve: (response: Response) => void } {
  let resolvePromise: (response: Response) => void = () => undefined;
  const promise = new Promise<Response>((resolve) => {
    resolvePromise = resolve;
  });
  return { promise, resolve: resolvePromise };
}

async function renderReadyWidget(expectedComments = 1): Promise<{ root: HTMLElement; shadowRoot: ShadowRoot }> {
  const root = document.createElement("div");
  document.body.append(root);
  renderWidget(root, {
    siteId,
    apiBaseUrl: "https://api.example.test",
    pageUrl: "https://site.example.test/article",
    target: root,
    theme: "light"
  });
  const shadowRoot = root.shadowRoot;
  if (!shadowRoot) {
    throw new Error("Shadow DOM виджета не создан");
  }
  await vi.waitFor(() => {
    expect(shadowRoot.querySelectorAll(".cloud-comment__comment")).toHaveLength(expectedComments);
  });
  return { root, shadowRoot };
}

function typeDraft(textarea: HTMLTextAreaElement, value: string): void {
  textarea.value = value;
  textarea.dispatchEvent(new Event("input", { bubbles: true }));
}

function composer(shadowRoot: ShadowRoot): HTMLTextAreaElement {
  const textarea = shadowRoot.querySelector<HTMLTextAreaElement>("[data-cloud-comment-form='comment'] textarea");
  if (!textarea) {
    throw new Error("Редактор комментария не найден");
  }
  return textarea;
}

async function login(shadowRoot: ShadowRoot, emailValue: string): Promise<void> {
  shadowRoot.querySelector<HTMLButtonElement>("[data-auth-action='expand']")?.click();
  const authForm = shadowRoot.querySelector<HTMLFormElement>("[data-cloud-comment-form='auth']");
  const email = authForm?.querySelector<HTMLInputElement>("input[name='email']");
  const password = authForm?.querySelector<HTMLInputElement>("input[name='password']");
  if (!authForm || !email || !password) {
    throw new Error("Форма входа не открылась");
  }
  email.value = emailValue;
  password.value = "password-123";
  authForm.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));
  await vi.waitFor(() => expect(composer(shadowRoot).readOnly).toBe(false));
}

beforeEach(() => {
  document.body.replaceChildren();
  window.localStorage.clear();
  window.sessionStorage.clear();
  window.localStorage.setItem(WIDGET_AUTH_TOKEN_KEY, token);
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("пагинация корневых комментариев", () => {
  it("догружает 21-й комментарий и скрывает кнопку на последней странице", async () => {
    const comments = rootComments(21);
    const requestedPages: number[] = [];
    installApiMock({
      onList: (url) => {
        const page = Number(url.searchParams.get("page"));
        requestedPages.push(page);
        return jsonResponse({
          items: page === 1 ? comments.slice(0, 20) : comments.slice(20),
          page,
          pageSize: 20,
          totalItems: comments.length,
          totalPages: 2
        });
      }
    });

    const { shadowRoot } = await renderReadyWidget(20);
    expect(shadowRoot.querySelectorAll(".cloud-comment__comment")).toHaveLength(20);
    const loadMore = shadowRoot.querySelector<HTMLButtonElement>("[data-load-comments='true']");
    expect(loadMore?.textContent).toBe("Показать ещё комментарии (1)");

    loadMore?.click();

    await vi.waitFor(() => {
      expect(shadowRoot.querySelectorAll(".cloud-comment__comment")).toHaveLength(21);
    });
    expect(shadowRoot.querySelector("[data-load-comments='true']")).toBeNull();
    expect(requestedPages).toEqual([1, 2]);
  });

  it("показывает возвращённые backend собственные pending root и reply после загрузки", async () => {
    const pendingRoot = publicComment({
      id: "pending-root",
      content: "Корневой комментарий ожидает проверку",
      status: "PENDING"
    });
    const pendingReply = publicComment({
      id: "pending-reply",
      parentId: "approved-root",
      content: "Ответ ожидает проверку",
      status: "PENDING"
    });
    const approvedRoot = publicComment({
      id: "approved-root",
      replies: [pendingReply],
      replyCount: 1
    });
    installApiMock({
      onList: () => jsonResponse({
        items: [pendingRoot, approvedRoot],
        page: 1,
        pageSize: 20,
        totalItems: 2,
        totalPages: 1
      })
    });

    const { shadowRoot } = await renderReadyWidget(3);

    expect(shadowRoot.textContent).toContain("Корневой комментарий ожидает проверку");
    expect(shadowRoot.textContent).toContain("Ответ ожидает проверку");
    expect(shadowRoot.querySelectorAll(".cloud-comment__status")).toHaveLength(2);
  });
});

describe("устойчивый черновик и фокус виджета", () => {
  it("сохраняет точный черновик и фокус при реакции, открытии профиля и смене сортировки", async () => {
    const api = installApiMock();
    const { shadowRoot } = await renderReadyWidget();
    const draft = "  Черновик без потерь  ";
    typeDraft(composer(shadowRoot), draft);

    let reaction = shadowRoot.querySelector<HTMLButtonElement>("[data-reaction-type='LIKE']");
    expect(reaction).not.toBeNull();
    reaction?.focus();
    reaction?.click();
    await vi.waitFor(() => {
      reaction = shadowRoot.querySelector<HTMLButtonElement>("[data-reaction-type='LIKE']");
      expect(reaction?.textContent).toContain("1");
      expect(shadowRoot.activeElement).toBe(reaction);
    });
    expect(composer(shadowRoot).value).toBe(draft);

    let profile = shadowRoot.querySelector<HTMLButtonElement>("[data-profile-action='toggle']");
    expect(profile).not.toBeNull();
    profile?.focus();
    profile?.click();
    profile = shadowRoot.querySelector<HTMLButtonElement>("[data-profile-action='toggle']");
    expect(profile?.getAttribute("aria-expanded")).toBe("true");
    expect(shadowRoot.activeElement).toBe(profile);
    expect(composer(shadowRoot).value).toBe(draft);

    let sort = shadowRoot.querySelector<HTMLSelectElement>("[data-comment-sort='true']");
    expect(sort).not.toBeNull();
    sort?.focus();
    if (sort) {
      sort.value = "NEWEST";
      sort.dispatchEvent(new Event("change", { bubbles: true }));
    }
    await vi.waitFor(() => {
      expect(api.getListCalls()).toBe(2);
      sort = shadowRoot.querySelector<HTMLSelectElement>("[data-comment-sort='true']");
      expect(sort?.value).toBe("NEWEST");
      expect(shadowRoot.activeElement).toBe(sort);
    });
    expect(composer(shadowRoot).value).toBe(draft);
  });

  it("сохраняет точный текст, фокус и выделение после ошибки POST 500", async () => {
    installApiMock({ onPost: () => jsonResponse({ error: { message: "Ошибка сохранения" } }, 500) });
    const { shadowRoot } = await renderReadyWidget();
    const draft = "  Не потерять этот текст  ";
    const textarea = composer(shadowRoot);
    typeDraft(textarea, draft);
    textarea.focus();
    textarea.setSelectionRange(2, 11, "forward");
    const submit = textarea.form?.querySelector<HTMLButtonElement>("button[type='submit']");
    submit?.focus();
    submit?.click();

    await vi.waitFor(() => expect(shadowRoot.textContent).toContain("Ошибка сохранения"));
    const restored = composer(shadowRoot);
    expect(restored.value).toBe(draft);
    expect(shadowRoot.activeElement).toBe(restored);
    expect([restored.selectionStart, restored.selectionEnd, restored.selectionDirection]).toEqual([2, 11, "forward"]);
  });

  it("сохраняет черновик редактирования, фокус и выделение после ошибки PATCH 500", async () => {
    installApiMock({ onPatch: () => jsonResponse({ error: { message: "Правка не сохранена" } }, 500) });
    const { shadowRoot } = await renderReadyWidget();
    shadowRoot.querySelector<HTMLButtonElement>("[data-comment-action='edit']")?.click();
    const textarea = shadowRoot.querySelector<HTMLTextAreaElement>("[data-cloud-comment-form='edit-comment'] textarea");
    expect(textarea).not.toBeNull();
    const draft = "  Исправленная версия  ";
    if (!textarea) {
      return;
    }
    typeDraft(textarea, draft);
    textarea.focus();
    textarea.setSelectionRange(2, 14, "backward");
    const submit = textarea.form?.querySelector<HTMLButtonElement>("button[type='submit']");
    submit?.focus();
    submit?.click();

    await vi.waitFor(() => expect(shadowRoot.textContent).toContain("Правка не сохранена"));
    const restored = shadowRoot.querySelector<HTMLTextAreaElement>("[data-cloud-comment-form='edit-comment'] textarea");
    expect(restored?.value).toBe(draft);
    expect(shadowRoot.activeElement).toBe(restored);
    expect([restored?.selectionStart, restored?.selectionEnd, restored?.selectionDirection]).toEqual([2, 14, "backward"]);
  });

  it("восстанавливает комментарий и позволяет повторить удаление после ошибки DELETE", async () => {
    installApiMock({
      onDelete: () => jsonResponse({ error: { message: "Удаление не выполнено" } }, 503)
    });
    const { shadowRoot } = await renderReadyWidget();

    shadowRoot.querySelector<HTMLButtonElement>("[data-comment-action='ask-delete']")?.click();
    shadowRoot.querySelector<HTMLButtonElement>("[data-comment-action='confirm-delete']")?.click();

    await vi.waitFor(() => expect(shadowRoot.textContent).toContain("Удаление не выполнено"));
    expect(shadowRoot.textContent).toContain("Исходный комментарий");
    const retry = shadowRoot.querySelector<HTMLButtonElement>("[data-comment-action='confirm-delete']");
    expect(retry).not.toBeNull();
    expect(retry?.disabled).toBe(false);
  });

  it("после успешной правки возвращает фокус на кнопку редактирования", async () => {
    installApiMock();
    const { shadowRoot } = await renderReadyWidget();
    shadowRoot.querySelector<HTMLButtonElement>("[data-comment-action='edit']")?.click();
    const textarea = shadowRoot.querySelector<HTMLTextAreaElement>("[data-cloud-comment-form='edit-comment'] textarea");
    expect(textarea).not.toBeNull();
    if (!textarea) {
      return;
    }
    typeDraft(textarea, "Сохранённая версия");
    const submit = textarea.form?.querySelector<HTMLButtonElement>("button[type='submit']");
    submit?.focus();
    submit?.click();

    await vi.waitFor(() => expect(shadowRoot.querySelector("[data-cloud-comment-form='edit-comment']")).toBeNull());
    const editTrigger = shadowRoot.querySelector<HTMLButtonElement>("[data-comment-action='edit']");
    expect(editTrigger).not.toBeNull();
    expect(shadowRoot.activeElement).toBe(editTrigger);
  });

  it("после отмены правки возвращает фокус на кнопку редактирования", async () => {
    installApiMock();
    const { shadowRoot } = await renderReadyWidget();
    shadowRoot.querySelector<HTMLButtonElement>("[data-comment-action='edit']")?.click();
    const cancel = shadowRoot.querySelector<HTMLButtonElement>("[data-comment-action='cancel-edit']");
    expect(cancel).not.toBeNull();
    cancel?.focus();
    cancel?.click();

    const editTrigger = shadowRoot.querySelector<HTMLButtonElement>("[data-comment-action='edit']");
    expect(editTrigger).not.toBeNull();
    expect(shadowRoot.activeElement).toBe(editTrigger);
  });

  it("учитывает ожидающий модерации ответ, если публичный replyCount его ещё не включает", async () => {
    const rootComment = publicComment();
    installApiMock({
      onPost: () => jsonResponse(publicComment({
        id: "pending-reply",
        parentId: rootComment.id,
        content: "Ответ на проверке",
        status: "PENDING"
      }), 201)
    });
    const { shadowRoot } = await renderReadyWidget();
    shadowRoot.querySelector<HTMLButtonElement>(`[data-reply-to='${rootComment.id}']`)?.click();
    const textarea = composer(shadowRoot);
    typeDraft(textarea, "Ответ на проверке");
    textarea.form?.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));

    await vi.waitFor(() => expect(shadowRoot.textContent).toContain("Ответ для Анна отправлен на проверку"));
    const toggle = shadowRoot.querySelector<HTMLButtonElement>(`[data-toggle-replies='${rootComment.id}']`);
    expect(toggle).not.toBeNull();
    toggle?.click();
    expect(shadowRoot.querySelector<HTMLButtonElement>(`[data-toggle-replies='${rootComment.id}']`)?.textContent)
      .toBe("Показать ответы (1)");
  });

  it("явный выход очищает черновик перед входом другого пользователя", async () => {
    installApiMock();
    const { shadowRoot } = await renderReadyWidget();
    typeDraft(composer(shadowRoot), "Черновик первого пользователя");
    shadowRoot.querySelector<HTMLButtonElement>("[data-profile-action='toggle']")?.click();
    shadowRoot.querySelector<HTMLButtonElement>("[data-account-action='logout']")?.click();

    await vi.waitFor(() => expect(composer(shadowRoot).readOnly).toBe(true));
    expect(composer(shadowRoot).value).toBe("");
    await login(shadowRoot, "new-user@example.test");
    expect(composer(shadowRoot).value).toBe("");
    expect(shadowRoot.textContent).toContain("Вы вошли как new-user@example.test");
    expect(window.localStorage.getItem(WIDGET_AUTH_TOKEN_KEY)).toBe("new-widget-token");
  });

  it("атомарный logout не позволяет старым 401 восстановить draft или сбросить новую сессию", async () => {
    const reactionResponse = deferredResponse();
    const logoutResponse = deferredResponse();
    const onReaction = vi.fn(() => reactionResponse.promise);
    const onLogout = vi.fn(() => logoutResponse.promise);
    const rootComment = publicComment();
    const draft = "Черновик до явного выхода";
    installApiMock({ loginEmail: "anna@example.test", onReaction, onLogout });
    const { shadowRoot } = await renderReadyWidget();
    shadowRoot.querySelector<HTMLButtonElement>(`[data-reply-to='${rootComment.id}']`)?.click();
    typeDraft(composer(shadowRoot), draft);
    shadowRoot.querySelector<HTMLButtonElement>("[data-reaction-type='LIKE']")?.click();
    await vi.waitFor(() => expect(onReaction).toHaveBeenCalledOnce());

    shadowRoot.querySelector<HTMLButtonElement>("[data-profile-action='toggle']")?.click();
    shadowRoot.querySelector<HTMLButtonElement>("[data-account-action='logout']")?.click();
    await vi.waitFor(() => expect(onLogout).toHaveBeenCalledOnce());
    expect(composer(shadowRoot).readOnly).toBe(true);
    expect(composer(shadowRoot).value).toBe("");
    expect(shadowRoot.querySelector(".cloud-comment__reply-context")).toBeNull();

    reactionResponse.resolve(jsonResponse({ error: { message: "Старый bearer 401" } }, 401));
    await new Promise((resolve) => setTimeout(resolve, 0));
    await login(shadowRoot, "anna@example.test");
    expect(composer(shadowRoot).value).toBe("");
    expect(shadowRoot.querySelector(".cloud-comment__reply-context")).toBeNull();

    logoutResponse.resolve(jsonResponse({ error: { message: "Поздний logout 401" } }, 401));
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(composer(shadowRoot).readOnly).toBe(false);
    expect(composer(shadowRoot).value).toBe("");
    expect(window.localStorage.getItem(WIDGET_AUTH_TOKEN_KEY)).toBe("new-widget-token");
    expect(shadowRoot.textContent).toContain("Вы вошли как anna@example.test");
    expect(shadowRoot.textContent).not.toContain("Старый bearer 401");
    expect(shadowRoot.textContent).not.toContain("Поздний logout 401");
  });

  it("после PATCH 401 восстанавливает правку только тому же владельцу", async () => {
    const draft = "  Правка после истечения сессии  ";
    installApiMock({
      loginEmail: "ANNA@EXAMPLE.TEST",
      onPatch: () => jsonResponse({ error: { message: "Сессия истекла" } }, 401)
    });
    const { shadowRoot } = await renderReadyWidget();
    shadowRoot.querySelector<HTMLButtonElement>("[data-comment-action='edit']")?.click();
    const textarea = shadowRoot.querySelector<HTMLTextAreaElement>("[data-cloud-comment-form='edit-comment'] textarea");
    if (!textarea) {
      throw new Error("Редактор правки не открылся");
    }
    typeDraft(textarea, draft);
    textarea.form?.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));

    await vi.waitFor(() => expect(composer(shadowRoot).readOnly).toBe(true));
    expect(shadowRoot.querySelector("[data-cloud-comment-form='edit-comment']")).toBeNull();
    await login(shadowRoot, "anna@example.test");
    const restored = shadowRoot.querySelector<HTMLTextAreaElement>("[data-cloud-comment-form='edit-comment'] textarea");
    expect(restored?.value).toBe(draft);
  });

  it("после POST 401 восстанавливает ответ тому же пользователю и валидному root", async () => {
    const rootComment = publicComment();
    const draft = "Ответ после повторного входа";
    installApiMock({
      loginEmail: "Anna@Example.Test",
      onPost: () => jsonResponse({ error: { message: "Сессия истекла" } }, 401)
    });
    const { shadowRoot } = await renderReadyWidget();
    shadowRoot.querySelector<HTMLButtonElement>(`[data-reply-to='${rootComment.id}']`)?.click();
    typeDraft(composer(shadowRoot), draft);
    composer(shadowRoot).form?.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));

    await vi.waitFor(() => expect(composer(shadowRoot).readOnly).toBe(true));
    expect(composer(shadowRoot).value).toBe("");
    expect(shadowRoot.querySelector(".cloud-comment__reply-context")).toBeNull();
    await login(shadowRoot, "anna@example.test");
    expect(composer(shadowRoot).value).toBe(draft);
    expect(shadowRoot.querySelector(".cloud-comment__reply-context")?.textContent).toContain("Ответ для Анна");
  });

  it("после POST 401 восстанавливает обычный корневой черновик тому же пользователю", async () => {
    const draft = "Корневой черновик после повторного входа";
    installApiMock({
      loginEmail: "anna@example.test",
      onPost: () => jsonResponse({ error: { message: "Сессия истекла" } }, 401)
    });
    const { shadowRoot } = await renderReadyWidget();
    typeDraft(composer(shadowRoot), draft);
    composer(shadowRoot).form?.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));

    await vi.waitFor(() => expect(composer(shadowRoot).readOnly).toBe(true));
    expect(composer(shadowRoot).value).toBe("");
    await login(shadowRoot, "ANNA@example.test");
    expect(composer(shadowRoot).value).toBe(draft);
    expect(shadowRoot.querySelector(".cloud-comment__reply-context")).toBeNull();
  });

  it("не показывает reply draft другому аккаунту и не превращает его в корневой", async () => {
    const rootComment = publicComment();
    const draft = "Чужой ответ не должен появиться";
    installApiMock({
      loginEmail: "other-user@example.test",
      onPost: () => jsonResponse({ error: { message: "Сессия истекла" } }, 401)
    });
    const { shadowRoot } = await renderReadyWidget();
    shadowRoot.querySelector<HTMLButtonElement>(`[data-reply-to='${rootComment.id}']`)?.click();
    typeDraft(composer(shadowRoot), draft);
    composer(shadowRoot).form?.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));

    await vi.waitFor(() => expect(composer(shadowRoot).readOnly).toBe(true));
    await login(shadowRoot, "other-user@example.test");
    expect(composer(shadowRoot).value).toBe("");
    expect(shadowRoot.querySelector(".cloud-comment__reply-context")).toBeNull();
    expect(shadowRoot.textContent).not.toContain(draft);
  });

  it("не превращает reply draft в корневой, если цель исчезла до повторного входа", async () => {
    const rootComment = publicComment();
    const draft = "Ответ для удалённой ветки";
    installApiMock({
      loginEmail: "anna@example.test",
      commentsAfterLogin: [],
      onPost: () => jsonResponse({ error: { message: "Сессия истекла" } }, 401)
    });
    const { shadowRoot } = await renderReadyWidget();
    shadowRoot.querySelector<HTMLButtonElement>(`[data-reply-to='${rootComment.id}']`)?.click();
    typeDraft(composer(shadowRoot), draft);
    composer(shadowRoot).form?.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));

    await vi.waitFor(() => expect(composer(shadowRoot).readOnly).toBe(true));
    await login(shadowRoot, "anna@example.test");
    expect(composer(shadowRoot).value).toBe("");
    expect(shadowRoot.querySelector(".cloud-comment__reply-context")).toBeNull();
  });

  it("не восстанавливает правку, если свежий комментарий больше не принадлежит пользователю", async () => {
    installApiMock({
      loginEmail: "anna@example.test",
      commentsAfterLogin: [publicComment({ ownedByCurrentUser: false })],
      onPatch: () => jsonResponse({ error: { message: "Сессия истекла" } }, 401)
    });
    const { shadowRoot } = await renderReadyWidget();
    shadowRoot.querySelector<HTMLButtonElement>("[data-comment-action='edit']")?.click();
    const textarea = shadowRoot.querySelector<HTMLTextAreaElement>("[data-cloud-comment-form='edit-comment'] textarea");
    if (!textarea) {
      throw new Error("Редактор правки не открылся");
    }
    typeDraft(textarea, "Правка больше не владельца");
    textarea.form?.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));

    await vi.waitFor(() => expect(composer(shadowRoot).readOnly).toBe(true));
    await login(shadowRoot, "anna@example.test");
    expect(shadowRoot.querySelector("[data-cloud-comment-form='edit-comment']")).toBeNull();
    expect(shadowRoot.querySelector("[data-comment-action='edit']")).toBeNull();
  });

  it("два отложенных 401 сохраняют единственный контекст истёкшей сессии", async () => {
    const reactionResponse = deferredResponse();
    const postResponse = deferredResponse();
    const onReaction = vi.fn(() => reactionResponse.promise);
    const onPost = vi.fn(() => postResponse.promise);
    const draft = "Черновик при двух одновременных 401";
    installApiMock({ loginEmail: "anna@example.test", onReaction, onPost });
    const { shadowRoot } = await renderReadyWidget();
    typeDraft(composer(shadowRoot), draft);
    shadowRoot.querySelector<HTMLButtonElement>("[data-reaction-type='LIKE']")?.click();
    composer(shadowRoot).form?.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));
    await vi.waitFor(() => {
      expect(onReaction).toHaveBeenCalledOnce();
      expect(onPost).toHaveBeenCalledOnce();
    });

    reactionResponse.resolve(jsonResponse({ error: { message: "Первая сессия истекла" } }, 401));
    await vi.waitFor(() => expect(composer(shadowRoot).readOnly).toBe(true));
    postResponse.resolve(jsonResponse({ error: { message: "Вторая старая 401" } }, 401));
    await new Promise((resolve) => setTimeout(resolve, 0));

    await login(shadowRoot, "anna@example.test");
    expect(composer(shadowRoot).value).toBe(draft);
    expect(shadowRoot.textContent).not.toContain("Вторая старая 401");
  });

  it("поздний 401 старого запроса не сбрасывает новую сессию и восстановленный draft", async () => {
    const reactionResponse = deferredResponse();
    const postResponse = deferredResponse();
    const onReaction = vi.fn(() => reactionResponse.promise);
    const onPost = vi.fn(() => postResponse.promise);
    const draft = "Черновик уже новой сессии";
    installApiMock({ loginEmail: "anna@example.test", onReaction, onPost });
    const { shadowRoot } = await renderReadyWidget();
    typeDraft(composer(shadowRoot), draft);
    shadowRoot.querySelector<HTMLButtonElement>("[data-reaction-type='LIKE']")?.click();
    composer(shadowRoot).form?.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));
    await vi.waitFor(() => {
      expect(onReaction).toHaveBeenCalledOnce();
      expect(onPost).toHaveBeenCalledOnce();
    });

    reactionResponse.resolve(jsonResponse({ error: { message: "Старая сессия истекла" } }, 401));
    await vi.waitFor(() => expect(composer(shadowRoot).readOnly).toBe(true));
    await login(shadowRoot, "anna@example.test");
    expect(composer(shadowRoot).value).toBe(draft);

    postResponse.resolve(jsonResponse({ error: { message: "Запоздалая старая 401" } }, 401));
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(composer(shadowRoot).readOnly).toBe(false);
    expect(composer(shadowRoot).value).toBe(draft);
    expect(window.localStorage.getItem(WIDGET_AUTH_TOKEN_KEY)).toBe("new-widget-token");
    expect(shadowRoot.textContent).toContain("Вы вошли как anna@example.test");
    expect(shadowRoot.textContent).not.toContain("Запоздалая старая 401");
  });

  it("не предлагает повторную отправку, если POST успешен, а обновление списка упало", async () => {
    installApiMock({ failListAfterPost: true });
    const { shadowRoot } = await renderReadyWidget();
    const textarea = composer(shadowRoot);
    typeDraft(textarea, "Новый комментарий");
    textarea.focus();
    textarea.form?.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));

    await vi.waitFor(() => expect(shadowRoot.textContent).toContain("Не отправляйте его повторно"));
    expect(composer(shadowRoot).value).toBe("");
    expect([...shadowRoot.querySelectorAll(".cloud-comment__comment")]
      .filter((comment) => comment.textContent?.includes("Исходный комментарий"))).toHaveLength(1);
    expect([...shadowRoot.querySelectorAll(".cloud-comment__comment")]
      .filter((comment) => comment.textContent?.includes("Новый комментарий"))).toHaveLength(1);
  });

  it("не возвращает фокус в виджет, если пользователь ушёл на внешний элемент во время запроса", async () => {
    let resolvePost: (response: Response) => void = () => {
      throw new Error("POST ещё не начался");
    };
    installApiMock({
      onPost: () => new Promise<Response>((resolve) => {
        resolvePost = resolve;
      })
    });
    const { shadowRoot } = await renderReadyWidget();
    const external = document.createElement("button");
    external.textContent = "Внешняя кнопка";
    document.body.append(external);

    const textarea = composer(shadowRoot);
    typeDraft(textarea, "Отложенный комментарий");
    textarea.focus();
    textarea.setSelectionRange(3, 9);
    textarea.form?.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));
    await vi.waitFor(() => expect(composer(shadowRoot).disabled).toBe(true));
    external.focus();
    expect(document.activeElement).toBe(external);
    resolvePost(jsonResponse({ error: { message: "Ошибка сохранения" } }, 500));

    await vi.waitFor(() => expect(shadowRoot.textContent).toContain("Ошибка сохранения"));
    expect(document.activeElement).toBe(external);
    expect(composer(shadowRoot).value).toBe("Отложенный комментарий");
  });
});
