import { describe, expect, it } from "vitest";

import { getPublicAuthorLabel, optimisticReactions, updateRepliesInTree } from "./render";
import type { CommentReaction, PublicComment } from "./types";

const reactions: CommentReaction[] = [
  { type: "LIKE", emoji: "👍", label: "Нравится", count: 2, reactedByCurrentUser: true },
  { type: "LOVE", emoji: "❤️", label: "Любовь", count: 1, reactedByCurrentUser: false },
  { type: "LAUGH", emoji: "😂", label: "Смешно", count: 0, reactedByCurrentUser: false },
  { type: "WOW", emoji: "😮", label: "Удивление", count: 0, reactedByCurrentUser: false }
];

function comment(id: string, replies: PublicComment[] = [], replyCount = replies.length): PublicComment {
  return {
    id,
    siteId: "site",
    pageId: "page",
    parentId: null,
    author: { id: "user", displayName: null },
    content: id,
    status: "APPROVED",
    createdAt: "2026-07-12T00:00:00Z",
    updatedAt: "2026-07-12T00:00:00Z",
    editedAt: null,
    pinned: false,
    ownedByCurrentUser: false,
    reactions,
    replyCount,
    replies
  };
}

describe("адаптивный виджет", () => {
  it("не использует email-подобное значение как публичное имя", () => {
    expect(getPublicAuthorLabel({ id: "user", displayName: null })).toBe("Участник");
    expect(getPublicAuthorLabel({ id: "user", displayName: "visitor@example.com" })).toBe("Участник");
    expect(getPublicAuthorLabel({ id: "user", displayName: "  Анна  " })).toBe("Анна");
  });

  it("оптимистично переключает единственную реакцию", () => {
    const next = optimisticReactions(reactions, "LOVE");

    expect(next.find((reaction) => reaction.type === "LIKE")).toMatchObject({ count: 1, reactedByCurrentUser: false });
    expect(next.find((reaction) => reaction.type === "LOVE")).toMatchObject({ count: 2, reactedByCurrentUser: true });
  });

  it("добавляет страницы ответов без дублей и сохраняет общий счётчик", () => {
    const first = comment("reply-1");
    const second = comment("reply-2");
    const root = comment("root", [first], 3);

    const result = updateRepliesInTree([root], "root", [first, second], 3);

    expect(result[0].replyCount).toBe(3);
    expect(result[0].replies.map((reply) => reply.id)).toEqual(["reply-1", "reply-2"]);
  });
});
