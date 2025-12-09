import { Alert, Container } from '@mantine/core';
import { IconAlertTriangle } from '@tabler/icons-react';
import { useAuth } from '../contexts/AuthContext';
import { Link } from 'react-router-dom';

export default function NotificationBanner() {
    const { user, isLoading } = useAuth();

    if (isLoading || !user) return null;

    if (user.discordUsername) return null;

    return (
        <div style={{ backgroundColor: 'var(--mantine-color-yellow-1)', borderBottom: '1px solid var(--mantine-color-yellow-3)' }}>
            <Container size="lg" py="xs">
                <Alert
                    variant="transparent"
                    color="yellow"
                    title="¡Atención!"
                    icon={<IconAlertTriangle />}
                    styles={{
                        root: { padding: 0 },
                        message: { color: 'var(--mantine-color-yellow-9)' },
                        title: { color: 'var(--mantine-color-yellow-9)' }
                    }}
                >
                    No tienes configurado tu usuario de Discord. Por favor ve a <Link to="/profile" style={{ fontWeight: 'bold', color: 'inherit' }}>Ajustes</Link> para configurarlo y recibir notificaciones de partidas.
                </Alert>
            </Container>
        </div>
    );
}
