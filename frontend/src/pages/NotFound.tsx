import { Link } from 'react-router-dom';
import { Compass } from 'lucide-react';
import { buttonClass } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/Feedback';

export function NotFoundPage() {
  return (
    <div className="card mx-auto mt-10 max-w-lg">
      <EmptyState icon={Compass} title="Page not found" description="The page you were looking for doesn't exist."
        action={<Link to="/" className={buttonClass()}>Back to overview</Link>} />
    </div>
  );
}
