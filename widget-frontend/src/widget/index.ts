import { DEFAULT_API_BASE_URL } from "./config";
import { renderWidget } from "./render";
import type {
  CloudCommentWidgetApi,
  CloudCommentWidgetInstance,
  CloudCommentWidgetOptions,
  WidgetTheme
} from "./types";

const DEFAULT_TARGET_ID = "cloud-comment-widget";

function resolveTarget(target?: string | HTMLElement): HTMLElement {
  if (target instanceof HTMLElement) {
    return target;
  }

  if (typeof target === "string") {
    const foundTarget = document.querySelector<HTMLElement>(target);
    if (!foundTarget) {
      throw new Error(`CloudComment target was not found: ${target}`);
    }
    return foundTarget;
  }

  const existingTarget = document.getElementById(DEFAULT_TARGET_ID);
  if (existingTarget) {
    return existingTarget;
  }

  const createdTarget = document.createElement("div");
  createdTarget.id = DEFAULT_TARGET_ID;
  const currentScript = document.currentScript;
  if (currentScript?.parentNode) {
    currentScript.parentNode.insertBefore(createdTarget, currentScript.nextSibling);
  } else {
    document.body.append(createdTarget);
  }
  return createdTarget;
}

function normalizeOptions(options: CloudCommentWidgetOptions): Required<CloudCommentWidgetOptions> {
  if (!options.siteId) {
    throw new Error("CloudComment siteId is required");
  }

  return {
    siteId: options.siteId,
    apiBaseUrl: options.apiBaseUrl ?? DEFAULT_API_BASE_URL,
    pageUrl: options.pageUrl ?? window.location.href,
    target: options.target ?? `#${DEFAULT_TARGET_ID}`,
    theme: normalizeTheme(options.theme)
  };
}

function normalizeTheme(theme?: string): WidgetTheme {
  return theme === "light" || theme === "dark" || theme === "auto" ? theme : "auto";
}

function init(options: CloudCommentWidgetOptions): CloudCommentWidgetInstance {
  const normalizedOptions = normalizeOptions(options);
  const target = resolveTarget(options.target);
  target.replaceChildren();
  return renderWidget(target, normalizedOptions);
}

function autoInit(): CloudCommentWidgetInstance | null {
  const script = document.currentScript as HTMLScriptElement | null;
  const siteId = script?.dataset.siteId;
  if (!siteId) {
    return null;
  }

  return init({
    siteId,
    apiBaseUrl: script.dataset.apiBaseUrl,
    pageUrl: script.dataset.pageUrl,
    target: script.dataset.target,
    theme: normalizeTheme(script.dataset.theme)
  });
}

const api: CloudCommentWidgetApi = {
  init,
  autoInit
};

window.CloudCommentWidget = api;
autoInit();

export { autoInit, init };
