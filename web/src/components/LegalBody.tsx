const URL_RE = /(https?:\/\/[^\s]+)/g;

/** Renders a legal section body, turning bare URLs into clickable links. */
export function LegalBody({ text }: { text: string }) {
  // split() with a capturing group alternates plain text (even indices)
  // and matched URLs (odd indices).
  const parts = text.split(URL_RE);
  return (
    <p className="mt-2 text-night/80">
      {parts.map((part, i) =>
        i % 2 === 1 ? (
          <a
            key={i}
            href={part}
            target="_blank"
            rel="noopener noreferrer"
            className="break-all underline hover:text-night"
          >
            {part}
          </a>
        ) : (
          part
        ),
      )}
    </p>
  );
}
