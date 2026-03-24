import React from 'react';
import { Link } from 'react-router-dom';

interface Props {
  content: string;
  className?: string;
  /** URLs to hide from inline text (because they'll be rendered as link previews) */
  suppressUrls?: string[];
}

interface TextNode {
  type: 'text' | 'bold' | 'italic' | 'link' | 'mention';
  value: string;
  href?: string;
  mentionId?: string;
}

function parseInline(text: string): TextNode[] {
  const nodes: TextNode[] = [];
  // Combined regex: @mentions, bold (**text** or *text*), italic (_text_), or URLs
  const pattern = /(@\[([^\]]+)\]\((\d+)\)|\*\*(.+?)\*\*|\*(.+?)\*|_(.+?)_|(https?:\/\/[^\s<]+))/g;
  let lastIndex = 0;
  let match: RegExpExecArray | null;

  while ((match = pattern.exec(text)) !== null) {
    // Add preceding plain text
    if (match.index > lastIndex) {
      nodes.push({ type: 'text', value: text.slice(lastIndex, match.index) });
    }

    if (match[2] && match[3]) {
      // @[Name](id) mention
      nodes.push({ type: 'mention', value: match[2], mentionId: match[3] });
    } else if (match[4]) {
      // **bold**
      nodes.push({ type: 'bold', value: match[4] });
    } else if (match[5]) {
      // *bold* (single asterisk)
      nodes.push({ type: 'bold', value: match[5] });
    } else if (match[6]) {
      // _italic_
      nodes.push({ type: 'italic', value: match[6] });
    } else if (match[7]) {
      // URL
      nodes.push({ type: 'link', value: match[7], href: match[7] });
    }

    lastIndex = match.index + match[0].length;
  }

  // Add remaining plain text
  if (lastIndex < text.length) {
    nodes.push({ type: 'text', value: text.slice(lastIndex) });
  }

  return nodes;
}

export default function RichContent({ content, className, suppressUrls }: Props) {
  if (!content) return null;
  const suppressSet = new Set(suppressUrls ?? []);
  const nodes = parseInline(content);

  return (
    <div className={`whitespace-pre-wrap ${className ?? ''}`}>
      {nodes.map((node, i) => {
        switch (node.type) {
          case 'bold':
            return <strong key={i}>{node.value}</strong>;
          case 'italic':
            return <em key={i}>{node.value}</em>;
          case 'mention':
            return (
              <Link key={i} to={`/profile/${node.mentionId}`} className="text-primary-600 font-medium hover:underline">
                @{node.value}
              </Link>
            );
          case 'link':
            // Hide URLs that will be rendered as link previews
            if (suppressSet.has(node.value)) return null;
            return (
              <a
                key={i}
                href={node.href}
                target="_blank"
                rel="noopener noreferrer"
                className="text-primary-600 hover:underline"
              >
                {node.value}
              </a>
            );
          default:
            return <React.Fragment key={i}>{node.value}</React.Fragment>;
        }
      })}
    </div>
  );
}
