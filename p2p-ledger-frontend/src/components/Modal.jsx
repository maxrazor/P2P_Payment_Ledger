import { X } from 'lucide-react';

export default function Modal({ title, onClose, children }) {
  return <div className="modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
    <section className="modal" role="dialog" aria-modal="true" aria-label={title}>
      <div className="modal-header"><h2>{title}</h2><button className="icon-btn" type="button" aria-label="Close dialog" onClick={onClose}><X size={18} /></button></div>
      {children}
    </section>
  </div>;
}
