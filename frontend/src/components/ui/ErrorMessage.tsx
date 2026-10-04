type ErrorMessageProps = { message: string };

export function ErrorMessage({ message }: ErrorMessageProps) {
  return (
    <div className="bg-red-400/10 border border-red-400/20 rounded-xl p-4 text-red-400 text-sm">
      {message}
    </div>
  );
}
