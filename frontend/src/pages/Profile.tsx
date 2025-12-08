import { Container, Title, Card, Stack, Text, Group, Avatar } from '@mantine/core';
import { useAuth } from '../contexts/AuthContext';

export default function Profile() {
    const { user } = useAuth();

    if (!user) return null;

    return (
        <Container size="sm">
            <Stack gap="lg">
                <Title order={2}>Mi Perfil</Title>

                <Card shadow="sm" padding="lg" radius="md" withBorder>
                    <Stack gap="md">
                        <Group>
                            <Avatar color={user.avatarColor} size="xl" radius="xl">
                                {user.username.charAt(0).toUpperCase()}
                            </Avatar>
                            <div>
                                <Text size="xl" fw={700}>
                                    {user.username}
                                </Text>
                                <Text size="sm" c="dimmed">
                                    {user.email}
                                </Text>
                            </div>
                        </Group>

                        <div>
                            <Text size="sm" c="dimmed" mb="xs">
                                Color de avatar
                            </Text>
                            <Group>
                                <div
                                    style={{
                                        width: 40,
                                        height: 40,
                                        borderRadius: '50%',
                                        backgroundColor: user.avatarColor,
                                        border: '2px solid var(--mantine-color-gray-6)',
                                    }}
                                />
                                <Text size="sm">{user.avatarColor}</Text>
                            </Group>
                        </div>
                    </Stack>
                </Card>
            </Stack>
        </Container>
    );
}
