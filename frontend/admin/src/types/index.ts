export interface User {
  id: string;
  email: string;
  username: string;
  avatar?: string;
  role: 'admin' | 'moderator' | 'user';
  createdAt: string;
}

export interface Comment {
  id: string;
  content: string;
  author: User;
  postId: string;
  status: 'pending' | 'approved' | 'rejected' | 'spam';
  likes: number;
  dislikes: number;
  createdAt: string;
  replies?: Comment[];
}