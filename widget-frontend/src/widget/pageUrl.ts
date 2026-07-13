export function resolvePageUrl(explicitPageUrl: string | undefined, currentPageUrl: string): string {
  const pageUrl = explicitPageUrl ?? currentPageUrl;
  const fragmentStart = pageUrl.indexOf("#");

  return fragmentStart === -1 ? pageUrl : pageUrl.slice(0, fragmentStart);
}
