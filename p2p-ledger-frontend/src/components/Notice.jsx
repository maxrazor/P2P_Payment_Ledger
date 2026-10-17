import { X } from 'lucide-react';

export default function Notice({ notice, onClose }) {
  if (!notice) return null;
  return <div className={`notice ${notice.type || 'info'}`} role="status">
    <span>{notice.text}</span>
    <button aria-label="Dismiss notice" onClick={onClose}><X size={15} /></button>
  </div>;
}
