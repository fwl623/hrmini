import React from 'react';
import AiChatPanel from '@/components/AiAssistant/AiChatPanel';
import '@/components/AiAssistant/ai.less';

const AdminAiChatPage: React.FC = () => (
  <div className="ai-page-fill">
    <AiChatPanel restoreFloatBall />
  </div>
);

export default AdminAiChatPage;
