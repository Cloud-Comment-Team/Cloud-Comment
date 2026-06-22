export type CloudCommentWidgetOptions = {
  siteId: string;
  apiBaseUrl?: string;
  pageUrl?: string;
  target?: string | HTMLElement;
};

export type CloudCommentWidgetInstance = {
  destroy: () => void;
};

export type CloudCommentWidgetApi = {
  init: (options: CloudCommentWidgetOptions) => CloudCommentWidgetInstance;
  autoInit: () => CloudCommentWidgetInstance | null;
};

declare global {
  interface Window {
    CloudCommentWidget?: CloudCommentWidgetApi;
  }
}
