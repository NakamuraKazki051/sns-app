import { Fragment } from "react";
import { Link } from "react-router";

/**
 * 本文の「#タグ」(F-22)。サーバー(Hashtags.java)と同じ決まり:
 * # か ＃ のあとの文字・数字・_ の並び(50字まで)。直前が文字・数字・_ なら取らない。数字だけは取らない
 */
const TAG = /(?<![\p{L}\p{N}_])[#＃]([\p{L}\p{N}_]{1,50})(?![\p{L}\p{N}_])/gu;
const DIGITS = /^\p{N}+$/u;

export type TextPart = { text: string; tag?: string };

/** 本文を、ふつうの文字とタグに分ける。タグは小文字にそろえて tag に入れる */
export function splitHashtags(text: string): TextPart[] {
  const parts: TextPart[] = [];
  let last = 0;
  for (const match of text.matchAll(TAG)) {
    if (DIGITS.test(match[1])) continue;
    const start = match.index;
    if (start > last) parts.push({ text: text.slice(last, start) });
    parts.push({ text: match[0], tag: match[1].toLowerCase() });
    last = start + match[0].length;
  }
  if (last < text.length) parts.push({ text: text.slice(last) });
  return parts;
}

export function hashtagPath(tag: string): string {
  return `/hashtag/${encodeURIComponent(tag)}`;
}

/** 本文を文字のまま出し、タグだけリンクにする(HTML としては解釈しない) */
export function TextWithHashtags({ text }: { text: string }) {
  return (
    <>
      {splitHashtags(text).map((part, i) =>
        part.tag ? (
          <Link key={i} to={hashtagPath(part.tag)} className="hashtag" onClick={(e) => e.stopPropagation()}>
            {part.text}
          </Link>
        ) : (
          <Fragment key={i}>{part.text}</Fragment>
        ),
      )}
    </>
  );
}
