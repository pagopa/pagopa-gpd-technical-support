export function joinUrl(...parts) {
  return parts
    .filter((part) => part !== null && part !== undefined && part !== '')
    .map((part, index) => {
      if (index === 0) {
        return String(part).replace(/\/+$/, '');
      }
      return String(part).replace(/^\/+|\/+$/g, '');
    })
    .join('/');
}
